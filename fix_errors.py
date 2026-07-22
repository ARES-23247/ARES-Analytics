import os

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

def fix_imports(content):
    if "com.ares.analytics.ui.components.core" not in content:
        lines = content.split('\n')
        last_import = -1
        for i, line in enumerate(lines):
            if line.startswith("import "):
                last_import = i
        if last_import != -1:
            lines.insert(last_import + 1, "import com.ares.analytics.ui.components.core.*")
        content = '\n'.join(lines)
    return content

def fix_braces(content):
    # count { and }
    open_b = content.count('{')
    close_b = content.count('}')
    
    # if close_b > open_b, remove the extra ones from the end
    if close_b > open_b:
        diff = close_b - open_b
        # remove 'diff' number of '}' from the end of the file
        for _ in range(diff):
            last_brace_idx = content.rfind('}')
            if last_brace_idx != -1:
                content = content[:last_brace_idx] + content[last_brace_idx+1:]
                
    # if open_b > close_b, add extra ones to the end
    if open_b > close_b:
        diff = open_b - close_b
        content += '\n}' * diff
        
    return content

for filename in files:
    filepath = os.path.join(DIR, filename)
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    content = fix_imports(content)
    content = fix_braces(content)
    
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

print("Fixes applied.")
