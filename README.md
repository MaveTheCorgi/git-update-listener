# Git Update Listener Plugin for IntelliJ IDEA

This plugin allows you to automatically run specific IntelliJ IDEA run configurations when changes are pushed to a GitHub repository.

## Features

- Listens for GitHub webhook push events on a configurable port
- Targets a specific branch that you can configure
- Automatically reruns a selected run configuration when the target branch is updated
- Discord webhook integration to receive notifications when updates trigger runs
- Fully configurable through the Settings UI

## Installation

1. Download the latest release from the [Releases page](https://github.com/MaveTheCorgi/git-update-listener/releases)
2. Install the plugin in IntelliJ IDEA:
    - Go to Settings → Plugins
    - Click the gear icon and select "Install Plugin from Disk..."
    - Select the downloaded .zip file

## Configuration

1. Open Settings → Tools → Git Update Listener
2. Select a run configuration to trigger
3. Specify the branch name to monitor (e.g., "main" or "develop")
4. Enter the port number to listen on (e.g., 12345)
5. (Optional) Add a Discord webhook URL to receive notifications

## Setting Up GitHub Webhooks

1. Go to your GitHub repository
2. Navigate to Settings → Webhooks → Add webhook
3. Set the Payload URL to `http://your-server-ip:your-port`
4. Select "Just the push event"
5. Click "Add webhook"

## Usage

Once configured, the plugin will automatically listen for GitHub webhook events and trigger the selected run configuration when changes are pushed to the specified branch.

## Requirements

- IntelliJ IDEA 2022.3 or newer

## Building from Source

To build the plugin from source:

1. Clone the repository
2. Open the project in IntelliJ IDEA
3. Run `./gradlew buildPlugin`

The plugin will be built in the `build/distributions` directory.

## License

This project is licensed under the MIT License - see the LICENSE file for details.