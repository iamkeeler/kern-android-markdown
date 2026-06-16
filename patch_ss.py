import re

ss_path = 'app/src/main/java/com/attachdesign/kern/ui/settings/SettingsScreen.kt'
with open(ss_path, 'r') as f:
    content = f.read()

# Add import
if "import com.attachdesign.kern.ui.components.MinimalOutlinedButton" not in content:
    content = content.replace("import com.attachdesign.kern.ui.theme.appFontFamily", "import com.attachdesign.kern.ui.theme.appFontFamily\nimport com.attachdesign.kern.ui.components.MinimalOutlinedButton\nimport com.attachdesign.kern.ui.components.MinimalOutlinedButtonSmall")

# Remove the button definitions at the end of the file
# We can find "// ─────────────────────────────────────────────────────────────────────────────" and delete everything after
split_marker = "// ─────────────────────────────────────────────────────────────────────────────"
if split_marker in content:
    content = content.split(split_marker)[0]

with open(ss_path, 'w') as f:
    f.write(content)
