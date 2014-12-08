import socket
import sys
import json
import string
import time
import urllib2
import getopt

DEFAULT_BATCH_SIZE = 10
MY_IP = urllib2.urlopen("http://169.254.169.254/latest/meta-data/public-ipv4").read()
MY_PORT = 10001

# Get and set the scheduler info and workfile from the arguments.

scheduler_info = ''
SCHEDULER_IP = ''
SCHEDULER_PORT = ''
workfile = ''

# Accepts command line arguments in format "client -s <IP_ADDRESS:PORT> -w <WORKLOAD_FILE>"

arguments = sys.argv[1:]

try:
    opts, args = getopt.getopt(arguments,"hs:w:",["saddress=","wfile="])
except getopt.GetoptError:
    print "Please run the program with correct syntax."
    print "Run Instruction : client -s <IP_ADDRESS:PORT> -w <WORKLOAD_FILE>"
    sys.exit(2)

for opt, arg in opts:
    if opt == '-h':
        print "Run Instruction : client -s <IP_ADDRESS:PORT> -w <WORKLOAD_FILE>"
        sys.exit()

    elif opt in ("-s", "--saddress"):
        try:
            scheduler_info = arg.split(':')
            SCHEDULER_IP = scheduler_info[0]
            SCHEDULER_PORT = int(scheduler_info[1])

        except:
            print "Please run the program with correct syntax."
            print "Run Instruction : client -s <IP_ADDRESS:PORT> -w <WORKLOAD_FILE>"
            sys.exit()

    elif opt in ("-w", "--wfile"):
        try:
            workfile = arg
        except:
            print "Please run the program with correct syntax."
            print "Run Instruction : client -s <IP_ADDRESS:PORT> -w <WORKLOAD_FILE>"
            sys.exit()

if not SCHEDULER_IP or not SCHEDULER_PORT or not workfile:
    print "Please run the program with correct syntax."
    print "Run Instruction : client -s <IP_ADDRESS:PORT> -w <WORKLOAD_FILE>"
    sys.exit()


################################################################################
# Client side client part - fetch and submit tasks.
################################################################################

# To check if the given file exists.

file_exits = 0

if os.path.isfile(workfile):
    file_exits = 1

if not file_exits:
    print "The given workfile does not exist. Please provide correct path and filename."
    sys.exit()


# Read the workload file and store tasks in list.

with open(workfile) as f:
    data = f.readlines()

total_task = len(data)
task_type = string.split(data[0])[0]


# To set animoto frame_size and task_size.

animoto_frame_size = 60

if task_type == 'animoto':
    total_task = total_task/animoto_frame_size


# Starting batch creation and submission.

submitted_tasks = {}

try:
    remaining_tasks = total_task
    count = 0
    current_batch_size = DEFAULT_BATCH_SIZE
    
    while(remaining_tasks>0):
        if remaining_tasks>current_batch_size:
            remaining_tasks = remaining_tasks - current_batch_size
        else:
            current_batch_size = remaining_tasks
            remaining_tasks = 0

        batch_obj = {}
        batch_obj["batch_length"] = current_batch_size
            
        if remaining_tasks > 0:
            batch_obj["next_batch"] = 1
        else:
            batch_obj["next_batch"] = 0
        
        task_objects = []
        
        for task_count in xrange(current_batch_size):
            task_obj = {}
            
            task_id = str(MY_IP) + '_' + str(MY_PORT) + '_' + str(int(time.time())) + str(count)
            
            submitted_tasks[task_id] = 0

            task_data = []
            
            # To wrap multiple URLs based on animoto_frame_size in one task object.
            
            if task_type == 'animoto':
                url_count = 0
                while url_count<animoto_frame_size:
                    task = data[count]
                    task_data.append(task)
                    url_count = url_count+1
                    count = count+1
            elif task_type == 'sleep':
                task = data[count]
                task_data.append(task)
                count = count + 1

            task_obj["task_id"] = task_id
            task_obj["task_type"] = task_type
            task_obj["task_data"] = task_data

            task_objects.append(task_obj)
        
        
        # To wrap the task_objects in one batch_object.
        
        batch_obj["task_objects"] = task_objects

        json_obj = json.dumps(batch_obj)
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect((SCHEDULER_IP, SCHEDULER_PORT))
        sock.sendall(json_obj)
        sock.close()

finally:
    pass


################################################################################
# client side server part - to receive task resluts.
################################################################################


# To start the server on MY_PORT

sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server_address = ('0.0.0.0', MY_PORT)
received_results = {}

sock.bind(server_address)


# Listen for incoming connections

sock.listen(1)


# Start receiving the response batches till next_batch = 0.

received_counter = 0
receiving_next_batch = 1
    
while (receiving_next_batch == 1):
    
    connection, client_address = sock.accept()

    received_json_batch = connection.recv(1024)
    received_batch = json.loads(received_json_batch)
    connection.close()

    receiving_next_batch = received_batch["next_batch"]

    task_results = []
    task_results = received_batch["task_results"]

    for task in task_results:
        submitted_tasks[task["task_id"]] = 1
        received_results[task["task_id"]] = task["task_result"]
        received_counter = received_counter+1


# To check if any submitted tasks were missing from the received results.

pending_tasks = []

for key in submitted_tasks:
    if not submitted_tasks[key]:
        pending_tasks.append(key)

if not pending_tasks:
    print "All the task results received successfully"

else:
    print "All the task results received successfully except below :"
    for item in pending_tasks:
        print item


#--------------------------------------------------
# operating on animoto task result - printing the URLs. Downloading any one of them for sample.
#--------------------------------------------------

if task_type == 'animoto':
    print "Successful task results : \n"
    for key in received_results:
        print key, ' : ', received_results[key]
