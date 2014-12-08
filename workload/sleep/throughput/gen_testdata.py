import random

# To generate the workload for throughput.

task = "sleep 0"

# Define number of tasks in workload file.
total_task = 150

writefile = "task_0_count_150"
wfile = open(writefile,"ab")

for count in xrange(total_task):
    wfile.write(task +'\n')
wfile.close()
