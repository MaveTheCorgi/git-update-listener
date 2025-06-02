# Git Update Listener for IntelliJ

An IntelliJ IDEA plugin that listens for GitHub webhooks and automatically reruns specified configurations when a target branch is updated.

## Features

- Listens for GitHub webhook push events
- Configurable target branch
- Automatically reruns a selected run configuration when the target branch is updated
- Fully configurable through the Settings UI

## Installation

### Option 1: From GitHub Releases (Manual Installation)

1. Go to the [Releases page](https://github.com/MaveTheCorgi/git-update-listener/releases)
2. Download the latest release ZIP file
3. In IntelliJ IDEA, go to Settings → Plugins
4. Click the gear icon and select "Install Plugin from Disk..."
5. Choose the downloaded ZIP file
6. Restart IntelliJ when prompted

### Option 2: From Custom Plugin Repository (Coming Soon)

## Setup

1. After installing the plugin, go to Settings → Tools → Git Update Listener
2. Select the run configuration you want to trigger
3. Set your target branch (default: "beta")
4. Configure the listen port (default: 12345)
5. Apply changes

## GitHub Webhook Setup

1. Go to your GitHub repository
2. Click Settings → Webhooks → Add webhook
3. Enter your Payload URL: `http://your-server:12345/`
4. Content type: `application/json`
5. Select "Just the push event"
6. Click "Add webhook"

## Usage

Once set up, the plugin will:
1. Listen on the configured port for webhook events
2. When a push to your target branch is detected, rerun the selected configuration
3. Log activity in the IntelliJ event log

## Troubleshooting

- Make sure your firewall allows connections on your configured port
- Check IntelliJ's event log for error messages
- Ensure the run configuration exists in your project

## Development

This project uses Gradle with the IntelliJ Plugin development plugin.