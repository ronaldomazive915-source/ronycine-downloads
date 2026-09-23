
import sys

file_path = 'app/src/main/java/com/example/ui/screens/PlayerScreen.kt'
logic_path = '/tmp/next_ep_logic.txt'

with open(logic_path, 'r') as f:
    logic = f.read()

with open(file_path, 'r') as f:
    lines = f.readlines()

new_lines = []
inserted = False
for i, line in enumerate(lines):
    if 'onTryAgain = {' in line and not inserted:
        new_lines.append(logic + '\n')
        inserted = True
    new_lines.append(line)

with open(file_path, 'w') as f:
    f.writelines(new_lines)
print("Successfully inserted logic.")
