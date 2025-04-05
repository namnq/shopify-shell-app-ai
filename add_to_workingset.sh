#!/bin/bash


# Define the project directory
PROJECT_DIR="/Users/nam/galaxy/apps/shopify-shell-app-ai"

# Find all files in the project directory and add them to the working set
find "$PROJECT_DIR" -type f | while read -r file; do
    echo "Adding $file to working set"
    # Command to add file to working set (this is a placeholder, replace with actual command)
    # e.g., code --add "$file"
done
