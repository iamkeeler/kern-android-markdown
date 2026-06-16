ss_path = 'app/src/main/java/com/attachdesign/kern/ui/settings/SettingsScreen.kt'
with open(ss_path, 'r') as f:
    content = f.read()

# Ensure we have the proper import
if "import com.attachdesign.kern.ui.theme.appFontFamily" not in content:
    # let's add it right after package
    content = content.replace("package com.attachdesign.kern.ui.settings", "package com.attachdesign.kern.ui.settings\n\nimport com.attachdesign.kern.ui.theme.appFontFamily\n")

with open(ss_path, 'w') as f:
    f.write(content)
