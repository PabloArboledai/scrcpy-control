import re

with open('app/src/main/java/org/client/scrcpy/MainActivity.java', 'r', encoding='utf-8') as f:
    code = f.read()

# Fix nested scrcpy_main() by ensuring there's a closing bracket before it
code = re.sub(
    r'checkAndLaunchTailscale\(\);\s*\}\s*@android\.annotation\.SuppressLint\(\"SourceLockedOrientationActivity\"\)',
    'checkAndLaunchTailscale();\n    }\n\n    @android.annotation.SuppressLint("SourceLockedOrientationActivity")',
    code, flags=re.DOTALL
)

with open('app/src/main/java/org/client/scrcpy/MainActivity.java', 'w', encoding='utf-8') as f:
    f.write(code)

print('Fixed bracket!')
