import os
import re

DIR = r"c:\Users\david\dev\robotics\ftc\ARES-Analytics\app\src\main\kotlin\com\ares\analytics\ui\components\dashboard"

files = [
    "BatteryHealthCard.kt",
    "BrownoutProtectionCard.kt",
    "MotorHealthCard.kt",
    "VisionQualityCard.kt",
    "PoseViewerCard.kt",
    "SystemHealthCard.kt",
    "EKFTelemetryCard.kt",
    "IMUVisualizerCard.kt",
    "PowerDistributionCard.kt",
    "ProfilingDiagnosticsCard.kt",
    "SessionSummaryCard.kt",
    "StateMachineTrackerCard.kt",
    "TrendsCard.kt",
    "TuningCard.kt"
]

def add_imports(content):
    if "com.ares.analytics.ui.components.core." not in content:
        # insert after the last import
        lines = content.split('\n')
        last_import = -1
        for i, line in enumerate(lines):
            if line.startswith("import "):
                last_import = i
        if last_import != -1:
            lines.insert(last_import + 1, "import com.ares.analytics.ui.components.core.*")
        content = '\n'.join(lines)
    return content

def refactor_card_container(content):
    # Pattern 1: Card(...) { Column(modifier = Modifier.padding(16.dp)) {
    pattern1 = re.compile(r"Card\(\s*modifier = (.*?),\s*colors = CardDefaults\.cardColors\(containerColor = (.*?)\),\s*shape = RoundedCornerShape\(12\.dp\)\s*\)\s*\{\s*Column\(\s*modifier = Modifier\.padding\(16\.dp\)(?:.*?)\)\s*\{", re.MULTILINE | re.DOTALL)
    
    def repl1(m):
        mod = m.group(1)
        bg = m.group(2)
        return f"AnalyticsCard(modifier = {mod}, backgroundColor = {bg}) {{"
        
    content = pattern1.sub(repl1, content)
    
    # Pattern 2: Card with just modifier, filling max width, inside TuningCard
    pattern2 = re.compile(r"Card\(\s*modifier = (.*?)\s*\.clip\(RoundedCornerShape\(12\.dp\)\)\s*\.background\(AresSurface\)\s*\.border\(1\.dp, AresBorder, RoundedCornerShape\(12\.dp\)\),\s*colors = CardDefaults\.cardColors\(containerColor = AresSurface\)\s*\)\s*\{", re.MULTILINE | re.DOTALL)
    def repl2(m):
        return f"AnalyticsCard(modifier = {m.group(1)}) {{"
    content = pattern2.sub(repl2, content)

    # Pattern 3: Column(modifier = modifier.clip... {
    pattern3 = re.compile(r"Column\(\s*modifier = modifier\s*\.clip\(RoundedCornerShape\(12\.dp\)\)\s*\.background\((.*?)\)\s*\.border\(1\.dp, AresBorder, RoundedCornerShape\(12\.dp\)\)\s*\.padding\(16\.dp\),\s*verticalArrangement = (.*?)\s*\)\s*\{", re.MULTILINE | re.DOTALL)
    def repl3(m):
        bg = m.group(1)
        arr = m.group(2)
        if bg == "AresSurface":
            return f"AnalyticsCard(modifier = modifier) {{\n        Column(verticalArrangement = {arr}) {{"
        else:
            return f"AnalyticsCard(modifier = modifier, backgroundColor = {bg}) {{\n        Column(verticalArrangement = {arr}) {{"
    content = pattern3.sub(repl3, content)

    # Same pattern3 but without vertical arrangement
    pattern4 = re.compile(r"Column\(\s*modifier = modifier\s*\.clip\(RoundedCornerShape\(12\.dp\)\)\s*\.background\((.*?)\)\s*\.border\(1\.dp, AresBorder, RoundedCornerShape\(12\.dp\)\)\s*\.padding\(16\.dp\)\s*\)\s*\{", re.MULTILINE | re.DOTALL)
    def repl4(m):
        bg = m.group(1)
        if bg == "AresSurface":
            return f"AnalyticsCard(modifier = modifier) {{"
        else:
            return f"AnalyticsCard(modifier = modifier, backgroundColor = {bg}) {{"
    content = pattern4.sub(repl4, content)

    return content

for filename in files:
    filepath = os.path.join(DIR, filename)
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    content = add_imports(content)
    content = refactor_card_container(content)
    
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

print("Card container refactoring done.")
