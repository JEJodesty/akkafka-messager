from websocket import create_connection
from kafka import KafkaConsumer
import threading

group = raw_input('Enter Group/Channel > ')
user = raw_input('Enter Username > ')
consumer = KafkaConsumer('channel-out')

def send(user):
    while True:
        msg = raw_input('\n'+user+' > ')
        conn.send(user+': '+msg)

def receive():
    for msg in consumer:
        out = str(msg.value)
        you = out.split(": ")[0].split(" - ")[1]
        if you == user:
            out = ""
        print('\n' + out)

if __name__ == "__main__":
    # connect

    conn = create_connection("ws://localhost:8080/" + group)
    print('Connected to remote host...')

    thread_receive = threading.Thread(target = receive)
    thread_receive.start()

    thread_send = threading.Thread(target = send(user))
    thread_send.start()

