import random

# To generate the workload for animoto tasks
# Base file containing 60 image URLs

filename = "urldata_1_task"

with open(filename) as f:
    url_data = f.readlines()

task = 100      # Number of tasks in target file
num_url = 60    # Number of URLs per task

total_url = task*num_url

# workload filename format : urldata_<number_of_tasks>_task

writefile = "urldata_100_task"

wfile = open(writefile,"ab")

for count in xrange(total_url):
    wfile.write(random.choice(url_data))

wfile.close()