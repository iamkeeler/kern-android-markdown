import re

ms_path = 'app/src/main/java/com/attachdesign/kern/ui/main/MainScreen.kt'
with open(ms_path, 'r') as f:
    content = f.read()

# Replace import from settings to components
content = content.replace("import com.attachdesign.kern.ui.settings.MinimalOutlinedButton", "import com.attachdesign.kern.ui.components.MinimalOutlinedButton")

with open(ms_path, 'w') as f:
    f.write(content)
