import os
import re

base_dir = os.path.dirname(os.path.abspath(__file__))
directories = [
    os.path.join(base_dir, "app", "src", "main", "kotlin", "com", "ares", "analytics"),
    os.path.join(base_dir, "gateway", "src", "main", "kotlin"),
    os.path.join(base_dir, "shared", "src", "main", "kotlin")
]

kdoc_template = """/**
 * High-level description: Handles data processing pipeline, UI state management (MVI), or Ktor endpoint logic.
 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.
 * Canvas-to-field coordinate transformation conventions applied where relevant.
 *
 * @param args relevant arguments
 * @return expected results
 */"""

files_updated = 0

for d in directories:
    for root, dirs, files in os.walk(d):
        for f in files:
            if f.endswith('.kt'):
                filepath = os.path.join(root, f)
                with open(filepath, 'r', encoding='utf-8') as file:
                    content = file.read()
                
                lines = content.split('\n')
                new_lines = []
                changed = False
                for i, line in enumerate(lines):
                    stripped = line.strip()
                    # simplistic check
                    is_decl = (stripped.startswith('class ') or 
                               stripped.startswith('data class ') or 
                               stripped.startswith('interface ') or 
                               stripped.startswith('object ') or 
                               stripped.startswith('enum class ') or
                               stripped.startswith('fun '))
                    
                    if is_decl:
                        if i > 0 and '*/' in lines[i-1]:
                            pass
                        elif 'override fun' in stripped or 'private fun' in stripped:
                            pass
                        else:
                            indent = line[:len(line) - len(line.lstrip())]
                            indented_doc = '\n'.join([indent + l for l in kdoc_template.split('\n')])
                            new_lines.append(indented_doc)
                            changed = True
                    new_lines.append(line)
                
                if changed:
                    with open(filepath, 'w', encoding='utf-8') as file:
                        file.write('\n'.join(new_lines))
                    files_updated += 1

print(f"Updated {files_updated} files.")
