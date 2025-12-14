# 🧠 Lucid Chat - IntelliJ Plugin

IntelliJ IDEA plugin for AI-powered code assistance via local or remote Ollama API. Provides a Copilot-like experience with chat and agent modes.

**Developed by Turknet DevOps Team**

## 🚀 Features

- **Chat Mode (Ask)**: Normal chat with Ollama
- **Agent Mode**: Analyzes entire project and provides code assistance
- **Persistent Chat History**: Workspace-based chat history with multiple sessions
- **File Mentioning**: Use @ to mention files/folders in chat
- **Config File Support**: YAML or JSON config files
- **Environment Variables**: Read settings from environment variables
- **IntelliJ Settings UI**: Configuration from settings panel

## 📦 Installation

### Building the Plugin

To build the plugin:
```bash
./gradlew buildPlugin
```

The built file is located at:
```
build/distributions/lucid-ollama-intellij-0.1.0-plugin.zip
```

### Installing to IntelliJ IDEA

#### Method 1: Install from ZIP File (Recommended)

1. **Open IntelliJ IDEA**
2. Go to **File > Settings** (or `Cmd+,` / `Ctrl+,`)
3. Select **Plugins** from the left menu
4. Click the **⚙️ (Settings)** icon in the top right
5. Select **Install Plugin from Disk...**
6. Select the following file:
   ```
   build/distributions/lucid-ollama-intellij-0.1.0-plugin.zip
   ```
7. Click **OK**
8. **Restart** IntelliJ (Restart IDE button will appear)

#### Method 2: Run Directly with Gradle (For Development)

```bash
cd /path/to/lucid-jbx
./gradlew runIde
```

This command opens a new IntelliJ IDEA window with the plugin installed.

## ⚙️ Configuration

### Initial Setup

1. After restarting IntelliJ:
2. Go to **File > Settings > Lucid Chat**
3. Configure the settings:
   - **Ollama Endpoint**: `http://localhost:11434` (or your Ollama address)
   - **Model Name**: `llama3` (or the model you use)
   - **API Key**: (optional) If Ollama requires an API key
   - Configure other settings as needed

### Configuration Methods

#### 1. IntelliJ Settings UI

You can configure settings from `File > Settings > Lucid Chat`.

#### 2. Config File

Create `.lucid/config.yaml` or `.lucid/config.json` file in project root or home directory:

**config.yaml example:**
```yaml
ollamaEndpoint: "http://localhost:11434"
modelName: "llama3"
ollamaApiKey: ""
ollamaApiKeyHeaderName: "Authorization"
enableInlineCompletion: true
inlineCompletionTemperature: 0.2
enableStreamingStatus: false
ollamaExtraHeaders:
  X-Request-Source: "lucid-intellij"
```

**config.json example:**
```json
{
  "ollamaEndpoint": "http://localhost:11434",
  "modelName": "llama3",
  "ollamaApiKey": "",
  "enableInlineCompletion": true
}
```

#### 3. Environment Variables

```bash
export OLLAMA_API_KEY="llm-..."
export OLLAMA_ENDPOINT="http://localhost:11434"
export OLLAMA_MODEL="llama3"
export OLLAMA_EXTRA_HEADERS='{"X-Request-Source":"lucid-intellij"}'
```

### Priority Order

1. **IntelliJ Settings UI** (highest priority)
2. **Config File** (medium priority)
3. **Environment Variables** (lowest priority)

## 🕹️ Usage

### Opening the Tool Window

1. Open tool window from `Tools > Show Lucid Chat`
   - Or find the **Lucid Chat** tab among the tool windows on the right

### Using Chat

1. When the tool window opens, the chat interface will appear
2. Select **Ask** or **Agent** mode:
   - **Ask**: Normal chat mode
   - **Agent**: Analyzes project files and provides code assistance
3. Type a prompt and click **Send**
4. Responses from Ollama will be displayed in the chat panel

### Ask Mode

Used like normal chat. Responses from Ollama are displayed in the chat panel.

**Example:**
```
Prompt: "Write a function to reverse a string in Kotlin"
```

### Agent Mode

Analyzes project files and provides code assistance. You can mention specific files using @:
- Type `@` to see file list
- Select files to include in context
- Agent will prioritize mentioned files

**Example:**
```
@AgentHandler.kt What does this file do?
@plugin/src/main/kotlin/com/lucid/chat/ Review this folder
```

## 🔄 Updating the Plugin

1. Get a new build:
   ```bash
   ./gradlew buildPlugin
   ```
2. In IntelliJ, go to **File > Settings > Plugins**
3. Find the **Lucid Chat** plugin
4. Click **Update** or remove the old plugin and install the new one

## 🐛 Troubleshooting

### Plugin not visible
- Restart IntelliJ
- Do **File > Invalidate Caches / Restart**

### Ollama connection error
- Make sure Ollama is running: `curl http://localhost:11434/api/tags`
- Check that the endpoint in Settings is correct

### Tool window not opening
- Select **Lucid Chat** from **View > Tool Windows** menu
- Or run the **Tools > Show Lucid Chat** command

## 🔧 Development

```bash
# Build the plugin
./gradlew build

# Test in IDE
./gradlew runIde

# Package the plugin
./gradlew buildPlugin
```

## 📝 Notes

- Config files should be saved as `.lucid/config.yaml` or `.lucid/config.json`
- Config file is searched in project root, home directory, or current working directory
- Chat history is stored workspace-based under `~/.lucid-intellij/chatHistory/`
- Multiple chat sessions are supported - use "New Chat" to start a new session
- Chat history will be empty when the plugin is first opened
- Separate chat history is maintained for each workspace
- Settings changes take effect immediately (no restart required)

## 📄 License

MIT
