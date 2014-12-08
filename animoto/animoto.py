import urllib
import urllib2
import sys
import string
import subprocess
import socket
import json
from boto.s3.connection import S3Connection
from boto.s3.key import Key
import os


# Receive the port numbers as argument from worker.

port_numbers = sys.argv[1:]


# Start the server

MY_IP = 'localhost'
MY_PORT = int(port_numbers[0])

sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server_address = (MY_IP, MY_PORT)
sock.bind(server_address)


# Listen for incoming connections, receive the json object and close the connection.

sock.listen(1)
connection, client_address = sock.accept()

object_size = 8192
task_json_object = connection.recv(object_size)

connection.close()


# Try to unpack the json object. If error, set the error message

converted = 0

try:
    task_object = ''
    task_object = json.loads(task_json_object)
    converted =1
except:
    converted =0
    task_result = "There was some error reading the request message containing the URLs and TaskID."
    task_id = "temp_error_id"


# If json object converted successfully, process the data.

if converted:

    task_id = task_object["task_id"]
    task_data = task_object["task_data"]

    # Get URLs from the task object

    url_list = []

    for item in task_data:
        url_list.append(item.split(' ')[1])

    # Download the images

    counter = 1
    for url_data in url_list:
        if len(str(counter)) < 2:
            str_counter = '0'+ str(counter)
        else:
            str_counter = str(counter)
        imagename = "image" + str_counter + ".jpg"
        urllib.urlretrieve(url_data, imagename)
        counter = counter+1

    # Create a video

    frames_per_second = 1
    video_title = str(task_id) + ".mp4"
    FNULL = open(os.devnull, 'w')           # To write the output of subprocess to dev null.
    subprocess.call(["ffmpeg","-framerate",str(frames_per_second),"-i","image%02d.jpg","-c:v", "libx264","-r", "30","-pix_fmt","yuv420p", video_title], stdout=FNULL, stderr=subprocess.STDOUT)
    FNULL.close()

    # Connect to S3 bucket and store the video

    AWS_ACCESS_KEY_ID = ""      #Update manually
    AWS_SECRET_ACCESS_KEY = ""  #Update manually

    conn = S3Connection(AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)

    mybucket = conn.get_bucket('vapcs553prog4')
    key_object = Key(mybucket)
    key_object.key = video_title

    key_object.set_contents_from_filename(video_title)
    saved_key = mybucket.get_key(video_title)
    saved_key.set_canned_acl('public-read')


    # Create public URL foe that video

    image_url = saved_key.generate_url(0, query_auth=False, force_http=True)
    task_result = image_url


# create a response object

response_object = {}
response_object["task_id"] = task_id
response_object["task_result"] = task_result

response_json_object = json.dumps(response_object)


# return response to worker and close the socket.

client_info = task_id.split('_')
WORKER_IP = 'localhost'
WORKER_PORT =int(port_numbers[1])

sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

sock.connect((WORKER_IP, WORKER_PORT))

sock.sendall(response_json_object)
sock.close()
