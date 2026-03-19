#!/bin/bash
# Deploy KrishiMitra backend to HuggingFace Spaces
#
# Usage:
#   cd backend
#   bash deploy.sh <your-hf-username>
#
# Prerequisites:
#   1. HuggingFace account + access token
#   2. pip install huggingface_hub
#   3. huggingface-cli login (paste your token)

set -e

USERNAME=${1:-""}

if [ -z "$USERNAME" ]; then
    echo "Usage: bash deploy.sh <your-hf-username>"
    echo "Example: bash deploy.sh naraharisetty"
    exit 1
fi

SPACE_NAME="krishimitra"
REPO_URL="https://huggingface.co/spaces/$USERNAME/$SPACE_NAME"

echo "=== Deploying KrishiMitra to HuggingFace Spaces ==="
echo "Space: $REPO_URL"
echo ""

# Check if huggingface-cli is installed
if ! command -v huggingface-cli &> /dev/null; then
    echo "Installing huggingface_hub..."
    pip install huggingface_hub
fi

# Create the space repo locally
DEPLOY_DIR="/tmp/krishimitra-deploy"
rm -rf "$DEPLOY_DIR"
mkdir -p "$DEPLOY_DIR"

# Copy deployment files
cp README.md "$DEPLOY_DIR/"
cp Dockerfile "$DEPLOY_DIR/"
cp requirements.txt "$DEPLOY_DIR/"
cp -r app "$DEPLOY_DIR/app"

# Remove .env (secrets go to HF Spaces settings, not in the repo)
rm -f "$DEPLOY_DIR/app/.env" "$DEPLOY_DIR/.env"

echo "Files prepared in $DEPLOY_DIR"
echo ""
echo "Now push to HuggingFace Spaces:"
echo "  cd $DEPLOY_DIR"
echo "  git init"
echo "  git add ."
echo "  git commit -m 'Deploy KrishiMitra backend'"
echo "  git remote add space https://huggingface.co/spaces/$USERNAME/$SPACE_NAME"
echo "  git push space main --force"
echo ""
echo "Or use the HuggingFace web UI to upload files."
echo ""
echo "IMPORTANT: After deploying, go to Space Settings > Variables and secrets"
echo "and add these secrets:"
echo "  GEMINI_API_KEY"
echo "  GROQ_API_KEY"
echo "  SUPABASE_URL"
echo "  SUPABASE_KEY"
echo "  DATA_GOV_IN_API_KEY"
echo "  OPENWEATHER_API_KEY"
