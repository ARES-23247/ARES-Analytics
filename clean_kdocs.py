import os
import re

target_dir = r'c:\Users\david\dev\robotics\ftc\ARES-Analytics'

lines_to_remove = [
    r'^\s*\*\s*Provides high-performance, Zero-GC operations\.\s*$',
    r'^\s*\*\s*CCW-positive heading standard applied\.\s*$',
    r'^\s*\*\s*Note: Physical units use standard SI metrics\.\s*$',
    r'^\s*\*\s*Uses LaTeX math representation for kinematics where applicable\.\s*$',
    r'^\s*\*\s*High-level description: Handles data processing pipeline.*$',
    r'^\s*\*\s*Physical units: Distances in \\$.*$',
    r'^\s*\*\s*Canvas-to-field coordinate transformation conventions applied.*$',
    r'^\s*\*\s*@param args relevant arguments\s*$',
    r'^\s*\*\s*@return expected results\s*$',
    r'^\s*\*\s*[a-zA-Z0-9_]+ val\.\s*$',
    r'^\s*\*\s*[a-zA-Z0-9_]+ var\.\s*$'
]

def clean_kdoc(content):
    for pattern in lines_to_remove:
        content = re.sub(pattern, '', content, flags=re.MULTILINE)
    content = re.sub(r'^\s*/\*\*[\s\*]*\*/\s*\n', '', content, flags=re.MULTILINE)
    return content

changed_count = 0
for root, dirs, files in os.walk(target_dir):
    for file in files:
        if file.endswith('.kt'):
            filepath = os.path.join(root, file)
            
            # Read with appropriate encoding
            encoding_used = 'utf-8'
            try:
                with open(filepath, 'r', encoding='utf-8') as f:
                    content = f.read()
            except UnicodeDecodeError:
                encoding_used = 'utf-16'
                with open(filepath, 'r', encoding='utf-16') as f:
                    content = f.read()
            
            new_content = clean_kdoc(content)
            
            if new_content != content:
                with open(filepath, 'w', encoding=encoding_used) as f:
                    f.write(new_content)
                changed_count += 1
                print(f"Cleaned {file}")

print(f"Total files cleaned: {changed_count}")
