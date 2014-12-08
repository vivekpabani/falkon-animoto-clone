import random

# To generate the workload for efficiency.

task_types = ["sleep 0","sleep 1","sleep 2","sleep 4","sleep 8"]
worker_task_alloc = {1:80,2:40,4:20,8:10}
num_of_workers = [1,2,4,8,16]

# select task type, task per worker and number of workers from above details.

task = task_types[4]
task_per_worker = worker_task_alloc[8]
worker_count = num_of_workers[4]
total_task = task_per_worker*worker_count

writefile = "task_8_worker_16"
wfile = open(writefile,"ab")

for count in xrange(total_task):
    wfile.write(task +'\n')
wfile.close()