#!/usr/bin/env bash
# Creates the local git repo, verifies the build and pushes to a private GitHub repository "geomaster".
# Requirements: git, Node 22+ with corepack, GitHub CLI (gh) authenticated with `gh auth login`.
set -euo pipefail
cd "$(dirname "$0")/.."
corepack enable
pnpm install
pnpm build
git init -b main
git add .
git commit -m "chore: bootstrap GEOMASTER monorepo (Next.js web, Spring Boot core, ADRs, spec templates)"
gh repo create geomaster --private --source=. --remote=origin --push
echo "Done: private repo created and pushed."
