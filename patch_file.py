import re

with open("app/src/main/java/com/attachdesign/kern/ui/editor/EditorScreen.kt", "r") as f:
    content = f.read()

content = content.replace(
    "modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp)",
    "modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp, top = 16.dp, start = 16.dp)"
)

content = content.replace(
    "modifier = Modifier.shadow(elevation = 6.dp, shape = RoundedCornerShape(12.dp), clip = false),\n                                theme = uiState.activeTheme,",
    "theme = uiState.activeTheme,"
)


with open("app/src/main/java/com/attachdesign/kern/ui/editor/EditorScreen.kt", "w") as f:
    f.write(content)
