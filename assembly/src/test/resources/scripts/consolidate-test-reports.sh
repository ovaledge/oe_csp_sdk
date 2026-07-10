#!/usr/bin/env bash
# Consolidate Surefire, JaCoCo, and Allure outputs from reactor modules (unit/integration profile only).
set -euo pipefail
REPO_ROOT="${1:?repo root}"
CONSOLIDATED="${2:?consolidated dir}"
MODULES_CSV="${3:-}"
REPO_NAME="${4:-$(basename "${REPO_ROOT}")}"

mkdir -p "${CONSOLIDATED}/surefire" "${CONSOLIDATED}/jacoco" "${CONSOLIDATED}/allure-results"

maven_local_repository_paths() {
  local -a candidates=()
  local repo seen=" "
  if [ -n "${M2_REPO:-}" ]; then
    repo="${M2_REPO}"
    [[ "${repo}" == */repository ]] || repo="${repo%/}/repository"
    candidates+=("${repo}")
  fi
  if [ -n "${MAVEN_LOCAL_REPO:-}" ]; then
    repo="${MAVEN_LOCAL_REPO}"
    [[ "${repo}" == */repository ]] || repo="${repo%/}/repository"
    candidates+=("${repo}")
  fi
  if [ -n "${MAVEN_USER_HOME:-}" ]; then
    candidates+=("${MAVEN_USER_HOME}/repository")
  fi
  if [ -n "${M2_HOME:-}" ]; then
    candidates+=("${M2_HOME}/repository")
  fi
  if [ -n "${HOME:-}" ]; then
    candidates+=("${HOME}/.m2/repository")
  fi
  candidates+=("/var/lib/jenkins/.m2/repository")
  for repo in "${candidates[@]}"; do
    if [ -d "${repo}" ] && [[ "${seen}" != *" ${repo} "* ]]; then
      seen="${seen}${repo} "
      printf '%s\n' "${repo}"
    fi
  done
}

find_jacoco_cli() {
  local repo jar
  local -a jars=()
  while IFS= read -r repo; do
    [ -n "${repo}" ] || continue
    [ -d "${repo}/org/jacoco/org.jacoco.cli" ] || continue
    while IFS= read -r jar; do
      jars+=("${jar}")
    done < <(find "${repo}/org/jacoco/org.jacoco.cli" -name "org.jacoco.cli-*-nodeps.jar" 2>/dev/null)
  done < <(maven_local_repository_paths)
  if [ "${#jars[@]}" -eq 0 ]; then
    return 0
  fi
  printf '%s\n' "${jars[@]}" | sort -V | tail -1
}

read_artifact_id() {
  local pom="$1"
  grep -m1 '<artifactId>' "${pom}" 2>/dev/null | sed 's/.*<artifactId>\([^<]*\)<\/artifactId>.*/\1/' || echo "module"
}

resolve_module_paths() {
  local module_path="$1"
  module_path="$(echo "${module_path}" | xargs)"
  if [ "${module_path}" = "." ]; then
    mod_dir="${REPO_ROOT}"
    artifact_id="$(read_artifact_id "${REPO_ROOT}/pom.xml")"
  else
    artifact_id="${module_path##*/}"
    mod_dir="${REPO_ROOT}/${module_path}"
  fi
}

append_module_classfiles() {
  local target="$1"
  local classes_dir="${target}/classes"
  [ -d "${classes_dir}" ] || return 0
  local sub added=0
  for sub in "${classes_dir}"/*; do
    [ -d "${sub}" ] || continue
    if find "${sub}" -name '*.class' -print -quit | grep -q .; then
      CLASSFILE_ARGS+=(--classfiles "${sub}")
      added=1
    fi
  done
  if find "${classes_dir}" -maxdepth 1 -name '*.class' -print -quit | grep -q .; then
    local f has_non_class=0
    while IFS= read -r f; do
      case "${f}" in
        *.class) ;;
        *) has_non_class=1; break ;;
      esac
    done < <(find "${classes_dir}" -maxdepth 1 -type f 2>/dev/null)
    if [ "${has_non_class}" -eq 0 ]; then
      CLASSFILE_ARGS+=(--classfiles "${classes_dir}")
      added=1
    fi
  fi
  if [ "${added}" -eq 0 ]; then
    echo "WARN: no JaCoCo classfiles for ${target}"
  fi
}

append_module_sourcefiles() {
  local mod_dir="$1"
  local src_main="${mod_dir}/src/main/java"
  if [ -d "${src_main}" ]; then
    SOURCEFILE_ARGS+=(--sourcefiles "${src_main}")
  fi
  if [ -d "${mod_dir}/src/main/kotlin" ]; then
    SOURCEFILE_ARGS+=(--sourcefiles "${mod_dir}/src/main/kotlin")
  fi
  if [ -d "${mod_dir}/target/generated-sources" ]; then
    local gen
    for gen in "${mod_dir}/target/generated-sources"/*; do
      [ -d "${gen}" ] && SOURCEFILE_ARGS+=(--sourcefiles "${gen}")
    done
  fi
}

IFS=',' read -ra MODULES <<< "${MODULES_CSV}"
EXEC_FILES=()
CLASSFILE_ARGS=()
SOURCEFILE_ARGS=()

for module_path in "${MODULES[@]}"; do
  [ -z "$(echo "${module_path}" | xargs)" ] && continue
  resolve_module_paths "${module_path}"
  target="${mod_dir}/target"
  [ -d "${target}" ] || continue

  if [ -d "${target}/surefire-reports" ]; then
    mkdir -p "${CONSOLIDATED}/surefire/${artifact_id}"
    cp -R "${target}/surefire-reports/." "${CONSOLIDATED}/surefire/${artifact_id}/" 2>/dev/null || true
  fi
  if [ -f "${target}/site/surefire-report.html" ]; then
    mkdir -p "${CONSOLIDATED}/surefire/${artifact_id}"
    cp "${target}/site/surefire-report.html" "${CONSOLIDATED}/surefire/${artifact_id}/" 2>/dev/null || true
  fi
  if [ -d "${target}/site/jacoco" ]; then
    mkdir -p "${CONSOLIDATED}/jacoco/${artifact_id}"
    cp -R "${target}/site/jacoco/." "${CONSOLIDATED}/jacoco/${artifact_id}/" 2>/dev/null || true
  fi
  if [ -f "${target}/jacoco.exec" ]; then
    EXEC_FILES+=("${target}/jacoco.exec")
  fi
  append_module_classfiles "${target}"
  append_module_sourcefiles "${mod_dir}"
  if [ -d "${target}/allure-results" ]; then
    cp -R "${target}/allure-results/." "${CONSOLIDATED}/allure-results/" 2>/dev/null || true
  fi
done

JACOCO_CLI="$(find_jacoco_cli)"
if [ -n "${JACOCO_CLI}" ] && [ -f "${JACOCO_CLI}" ] && [ "${#EXEC_FILES[@]}" -gt 0 ] && [ "${#CLASSFILE_ARGS[@]}" -gt 0 ]; then
  mkdir -p "${CONSOLIDATED}/jacoco/aggregate"
  MERGED_EXEC="${CONSOLIDATED}/jacoco/merged.exec"
  rm -f "${MERGED_EXEC}"
  java -jar "${JACOCO_CLI}" merge "${EXEC_FILES[@]}" --destfile "${MERGED_EXEC}"
  REPORT_ARGS=(
    report "${MERGED_EXEC}"
    "${CLASSFILE_ARGS[@]}"
    --encoding UTF-8
    --name "${REPO_NAME} Unit+Integration Aggregate"
    --html "${CONSOLIDATED}/jacoco/aggregate"
    --xml "${CONSOLIDATED}/jacoco/aggregate/jacoco.xml"
    --csv "${CONSOLIDATED}/jacoco/aggregate/jacoco.csv"
  )
  if [ "${#SOURCEFILE_ARGS[@]}" -gt 0 ]; then
    REPORT_ARGS+=("${SOURCEFILE_ARGS[@]}")
  fi
  java -jar "${JACOCO_CLI}" "${REPORT_ARGS[@]}"
  echo "JaCoCo aggregate report: ${CONSOLIDATED}/jacoco/aggregate/index.html"
elif [ "${#EXEC_FILES[@]}" -eq 0 ]; then
  echo "WARN: No jacoco.exec files found; skipping merged aggregate report"
else
  echo "WARN: JaCoCo CLI not found; per-module JaCoCo copies only"
fi

INDEX="${CONSOLIDATED}/index.html"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EXTRACT_SUMMARY="${SCRIPT_DIR}/extract-build-summary.sh"

ALLURE_RESULT_COUNT=0
if [ -d "${CONSOLIDATED}/allure-results" ]; then
  ALLURE_RESULT_COUNT="$(find "${CONSOLIDATED}/allure-results" -name '*-result.json' 2>/dev/null | wc -l | tr -d ' ')"
fi
if [ "${ALLURE_RESULT_COUNT}" -gt 0 ] && command -v mvn >/dev/null 2>&1; then
  mkdir -p "${CONSOLIDATED}/allure"
  mvn -q io.qameta.allure:allure-maven:2.17.0:report \
    -f "${REPO_ROOT}/pom.xml" \
    -N \
    -Dallure.results.directory="${CONSOLIDATED}/allure-results" \
    -Dallure.report.directory="${CONSOLIDATED}/allure" \
    || echo "WARN: consolidated Allure report generation failed"
fi

{
  echo '<!DOCTYPE html><html><head><meta charset="UTF-8"><title>Consolidated test reports</title></head><body>'
  echo "<h1>${REPO_NAME} consolidated unit/integration test reports</h1><ul>"
  echo "<li><a href=\"jacoco/aggregate/index.html\">JaCoCo aggregate</a></li>"
  if [ -f "${CONSOLIDATED}/allure/index.html" ]; then
    echo "<li><a href=\"allure/index.html\">Allure (merged)</a></li>"
  fi
  for module_path in "${MODULES[@]}"; do
    [ -z "$(echo "${module_path}" | xargs)" ] && continue
    resolve_module_paths "${module_path}"
    module_links=""
    if [ -d "${CONSOLIDATED}/surefire/${artifact_id}" ]; then
      if [ -f "${CONSOLIDATED}/surefire/${artifact_id}/surefire-report.html" ]; then
        module_links="<a href=\"surefire/${artifact_id}/surefire-report.html\">Surefire</a>"
      else
        module_links="<a href=\"surefire/${artifact_id}/\">Surefire</a>"
      fi
    fi
    if [ -f "${CONSOLIDATED}/jacoco/${artifact_id}/index.html" ]; then
      [ -n "${module_links}" ] && module_links="${module_links} | "
      module_links="${module_links}<a href=\"jacoco/${artifact_id}/index.html\">JaCoCo</a>"
    fi
    [ -n "${module_links}" ] && echo "<li>${artifact_id}: ${module_links}</li>"
  done
  echo "<li><a href=\"metrics/build-summary.properties\">Build summary (properties)</a></li>"
  echo '</ul></body></html>'
} > "${INDEX}"

if [ -f "${EXTRACT_SUMMARY}" ]; then
  bash "${EXTRACT_SUMMARY}" "${CONSOLIDATED}" "${REPO_NAME}" || echo "WARN: build summary extraction failed"
fi
