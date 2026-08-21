#!/usr/bin/env bash
# Extract consolidated test and JaCoCo metrics into build-summary.properties (bash 3.2+; no Python).
set -euo pipefail

CONSOLIDATED="${1:?consolidated-reports directory}"
REPO_NAME="${2:-oasis_repo}"

METRICS_DIR="${CONSOLIDATED}/metrics"
SUMMARY_FILE="${METRICS_DIR}/build-summary.properties"
SUREFIRE_DIR="${CONSOLIDATED}/surefire"
ALLURE_RESULTS_DIR="${CONSOLIDATED}/allure-results"
JACOCO_XML="${CONSOLIDATED}/jacoco/aggregate/jacoco.xml"

# Metric values (plain variables for bash 3.2 on macOS)
repo_name="" build_date="" build_time_utc="" build_status=""
build_number="" build_url="" job_name="" git_branch="" git_commit=""
build_timestamp="" build_display_name="" node_name=""
reports_consolidated_dir="" reports_index="" reports_jacoco_aggregate=""
tests_total=0 tests_unit=0 tests_integration=0 tests_other=0
tests_failed=0 tests_errors=0 tests_skipped=0 tests_passed=0 modules_tested=0
tests_source="" reports_allure=""
coverage_lines_missed=0 coverage_lines_covered=0 coverage_lines_total=0 coverage_lines_pct="0.0"
coverage_instructions_missed=0 coverage_instructions_covered=0 coverage_instructions_pct="0.0"
coverage_branches_missed=0 coverage_branches_covered=0 coverage_branches_pct="0.0"
coverage_classes_missed=0 coverage_classes_covered=0

props_escape() {
  local v="$1"
  v="${v//$'\n'/ }"
  v="${v//$'\r'/ }"
  v="${v//\\/\\\\}"
  printf '%s' "$v"
}

xml_attr() {
  local file="$1" attr="$2"
  grep -o "${attr}=\"[^\"]*\"" "${file}" 2>/dev/null | head -1 | sed 's/^[^"]*"\([^"]*\)"$/\1/' || true
}

calc_pct() {
  local covered="$1" missed="$2"
  local total=$((covered + missed))
  if [ "${total}" -eq 0 ]; then
    echo "0.0"
    return
  fi
  awk -v c="${covered}" -v t="${total}" 'BEGIN { printf "%.1f", (100.0 * c / t) }'
}

parse_jacoco_xml() {
  local xml="$1"
  [ -f "${xml}" ] || return 0

  local line instr branch class
  line="$(grep -o '<counter type="LINE" missed="[0-9]*" covered="[0-9]*"' "${xml}" | tail -1 || true)"
  instr="$(grep -o '<counter type="INSTRUCTION" missed="[0-9]*" covered="[0-9]*"' "${xml}" | tail -1 || true)"
  branch="$(grep -o '<counter type="BRANCH" missed="[0-9]*" covered="[0-9]*"' "${xml}" | tail -1 || true)"
  class="$(grep -o '<counter type="CLASS" missed="[0-9]*" covered="[0-9]*"' "${xml}" | tail -1 || true)"
  [ -n "${line}" ] || return 0

  coverage_lines_missed="$(echo "${line}" | sed 's/.*missed="\([0-9]*\)".*/\1/')"
  coverage_lines_covered="$(echo "${line}" | sed 's/.*covered="\([0-9]*\)".*/\1/')"
  coverage_lines_total=$((coverage_lines_missed + coverage_lines_covered))
  coverage_lines_pct="$(calc_pct "${coverage_lines_covered}" "${coverage_lines_missed}")"

  coverage_instructions_missed="$(echo "${instr}" | sed 's/.*missed="\([0-9]*\)".*/\1/')"
  coverage_instructions_covered="$(echo "${instr}" | sed 's/.*covered="\([0-9]*\)".*/\1/')"
  coverage_instructions_pct="$(calc_pct "${coverage_instructions_covered}" "${coverage_instructions_missed}")"

  coverage_branches_missed="$(echo "${branch}" | sed 's/.*missed="\([0-9]*\)".*/\1/')"
  coverage_branches_covered="$(echo "${branch}" | sed 's/.*covered="\([0-9]*\)".*/\1/')"
  coverage_branches_pct="$(calc_pct "${coverage_branches_covered}" "${coverage_branches_missed}")"

  coverage_classes_missed="$(echo "${class}" | sed 's/.*missed="\([0-9]*\)".*/\1/')"
  coverage_classes_covered="$(echo "${class}" | sed 's/.*covered="\([0-9]*\)".*/\1/')"
}

classify_tests() {
  local suite_name="$1"
  case "${suite_name}" in
    *tests.unit*|*tests/unit*) echo "unit" ;;
    *tests.integration*|*tests/integration*) echo "integration" ;;
    *) echo "other" ;;
  esac
}

allure_field() {
  local file="$1" pattern="$2"
  grep -o "${pattern}" "${file}" 2>/dev/null | head -1 | sed 's/.*"\([^"]*\)"$/\1/' || true
}

# Prefer Allure: consolidated allure-results has one JSON per test method (Surefire copy is per-class XML only).
parse_allure() {
  local f status full_name package_name kind result_count
  if [ ! -d "${ALLURE_RESULTS_DIR}" ]; then
    return 1
  fi
  result_count="$(find "${ALLURE_RESULTS_DIR}" -name '*-result.json' 2>/dev/null | wc -l | tr -d ' ')"
  if [ "${result_count}" -eq 0 ]; then
    return 1
  fi

  tests_source="allure"
  reports_allure="${CONSOLIDATED}/allure/index.html"

  while IFS= read -r -d '' f; do
    status="$(allure_field "${f}" '"status":"[^"]*"')"
    full_name="$(allure_field "${f}" '"fullName":"[^"]*"')"
    package_name="$(grep -o '"name":"package","value":"[^"]*"' "${f}" 2>/dev/null | head -1 | sed 's/.*"value":"\([^"]*\)".*/\1/' || true)"
    if [ -z "${full_name}" ]; then
      full_name="${package_name}"
    fi
    [ -n "${status}" ] || continue

    tests_total=$((tests_total + 1))
    case "${status}" in
      passed) ;;
      failed|broken) tests_failed=$((tests_failed + 1)) ;;
      skipped) tests_skipped=$((tests_skipped + 1)) ;;
    esac

    kind="$(classify_tests "${full_name}")"
    if [ "${kind}" = "other" ] && [ -n "${package_name}" ]; then
      kind="$(classify_tests "${package_name}")"
    fi
    case "${kind}" in
      unit) tests_unit=$((tests_unit + 1)) ;;
      integration) tests_integration=$((tests_integration + 1)) ;;
      *) tests_other=$((tests_other + 1)) ;;
    esac
  done < <(find "${ALLURE_RESULTS_DIR}" -name '*-result.json' -print0 2>/dev/null)

  modules_tested="$(find "${ALLURE_RESULTS_DIR}" -name '*-result.json' -exec grep -h '"name":"testClass","value":"' {} + 2>/dev/null \
    | sed 's/.*"value":"\([^"]*\)".*/\1/' | sort -u | wc -l | tr -d ' ')"
  return 0
}

parse_surefire() {
  local xml rel module suite_name tests failures errs skip kind
  local -a seen_modules=()

  if [ ! -d "${SUREFIRE_DIR}" ]; then
    return 0
  fi

  while IFS= read -r -d '' xml; do
    rel="${xml#"${SUREFIRE_DIR}/"}"
    module="${rel%%/*}"
    seen_modules+=("${module}")

    suite_name="$(xml_attr "${xml}" "name")"
    tests="$(xml_attr "${xml}" "tests")"
    failures="$(xml_attr "${xml}" "failures")"
    errs="$(xml_attr "${xml}" "errors")"
    skip="$(xml_attr "${xml}" "skipped")"
    tests="${tests:-0}"
    failures="${failures:-0}"
    errs="${errs:-0}"
    skip="${skip:-0}"

    tests_total=$((tests_total + tests))
    tests_failed=$((tests_failed + failures))
    tests_errors=$((tests_errors + errs))
    tests_skipped=$((tests_skipped + skip))

    kind="$(classify_tests "${suite_name}")"
    case "${kind}" in
      unit) tests_unit=$((tests_unit + tests)) ;;
      integration) tests_integration=$((tests_integration + tests)) ;;
      *) tests_other=$((tests_other + tests)) ;;
    esac
  done < <(find "${SUREFIRE_DIR}" -name 'TEST-*.xml' -print0 2>/dev/null)

  if [ "${#seen_modules[@]}" -gt 0 ]; then
    modules_tested="$(printf '%s\n' "${seen_modules[@]}" | sort -u | wc -l | tr -d ' ')"
  fi
  tests_source="surefire"
  return 0
}

add_jenkins_env() {
  [ -n "${BUILD_NUMBER:-}" ] && build_number="${BUILD_NUMBER}"
  [ -n "${BUILD_URL:-}" ] && build_url="${BUILD_URL}"
  [ -n "${JOB_NAME:-}" ] && job_name="${JOB_NAME}"
  [ -n "${GIT_BRANCH:-}" ] && git_branch="${GIT_BRANCH}"
  [ -n "${GIT_COMMIT:-}" ] && git_commit="${GIT_COMMIT}"
  [ -n "${BUILD_TIMESTAMP:-}" ] && build_timestamp="${BUILD_TIMESTAMP}"
  [ -n "${BUILD_DISPLAY_NAME:-}" ] && build_display_name="${BUILD_DISPLAY_NAME}"
  [ -n "${NODE_NAME:-}" ] && node_name="${NODE_NAME}"
  [ -n "${BUILD_RESULT:-}" ] && build_status="${BUILD_RESULT}"
  return 0
}

write_properties() {
  mkdir -p "${METRICS_DIR}"
  {
    echo "# OvalEdge consolidated unit/integration build summary"
    echo "# Generated by extract-build-summary.sh - do not edit by hand"
    echo "repo.name=$(props_escape "${repo_name}")"
    echo "build.date=$(props_escape "${build_date}")"
    echo "build.time.utc=$(props_escape "${build_time_utc}")"
    [ -n "${build_status}" ] && echo "build.status=$(props_escape "${build_status}")"
    [ -n "${build_number}" ] && echo "build.number=$(props_escape "${build_number}")"
    [ -n "${build_url}" ] && echo "build.url=$(props_escape "${build_url}")"
    [ -n "${job_name}" ] && echo "job.name=$(props_escape "${job_name}")"
    [ -n "${git_branch}" ] && echo "git.branch=$(props_escape "${git_branch}")"
    [ -n "${git_commit}" ] && echo "git.commit=$(props_escape "${git_commit}")"
    [ -n "${build_timestamp}" ] && echo "build.timestamp=$(props_escape "${build_timestamp}")"
    [ -n "${build_display_name}" ] && echo "build.display.name=$(props_escape "${build_display_name}")"
    [ -n "${node_name}" ] && echo "node.name=$(props_escape "${node_name}")"
    echo "reports.consolidated.dir=$(props_escape "${reports_consolidated_dir}")"
    echo "reports.index=$(props_escape "${reports_index}")"
    echo "reports.jacoco.aggregate=$(props_escape "${reports_jacoco_aggregate}")"
    [ -n "${reports_allure}" ] && echo "reports.allure=$(props_escape "${reports_allure}")"
    [ -n "${tests_source}" ] && echo "tests.source=$(props_escape "${tests_source}")"
    echo "tests.total=${tests_total}"
    echo "tests.unit=${tests_unit}"
    echo "tests.integration=${tests_integration}"
    echo "tests.other=${tests_other}"
    echo "tests.passed=${tests_passed}"
    echo "tests.failed=${tests_failed}"
    echo "tests.errors=${tests_errors}"
    echo "tests.skipped=${tests_skipped}"
    echo "modules.tested=${modules_tested}"
    echo "coverage.lines.missed=${coverage_lines_missed}"
    echo "coverage.lines.covered=${coverage_lines_covered}"
    echo "coverage.lines.total=${coverage_lines_total}"
    echo "coverage.lines.pct=${coverage_lines_pct}"
    echo "coverage.instructions.missed=${coverage_instructions_missed}"
    echo "coverage.instructions.covered=${coverage_instructions_covered}"
    echo "coverage.instructions.pct=${coverage_instructions_pct}"
    echo "coverage.branches.missed=${coverage_branches_missed}"
    echo "coverage.branches.covered=${coverage_branches_covered}"
    echo "coverage.branches.pct=${coverage_branches_pct}"
    echo "coverage.classes.missed=${coverage_classes_missed}"
    echo "coverage.classes.covered=${coverage_classes_covered}"
  } > "${SUMMARY_FILE}"
}

main() {
  repo_name="${REPO_NAME}"
  build_date="$(date -u +%Y-%m-%d 2>/dev/null || date +%Y-%m-%d)"
  build_time_utc="$(date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date +%Y-%m-%dT%H:%M:%SZ)"
  reports_consolidated_dir="${CONSOLIDATED}"
  reports_index="${CONSOLIDATED}/index.html"
  reports_jacoco_aggregate="${CONSOLIDATED}/jacoco/aggregate/index.html"

  add_jenkins_env
  if ! parse_allure; then
    parse_surefire || true
  fi
  parse_jacoco_xml "${JACOCO_XML}"

  tests_passed=$((tests_total - tests_failed - tests_errors - tests_skipped))
  if [ "${tests_passed}" -lt 0 ]; then
    tests_passed=0
  fi

  if [ -z "${build_status}" ]; then
    if [ "${tests_failed}" -gt 0 ] || [ "${tests_errors}" -gt 0 ]; then
      build_status="FAILED"
    else
      build_status="SUCCESS"
    fi
  fi

  write_properties
  echo "Build summary: ${SUMMARY_FILE}"
}

main "$@"
