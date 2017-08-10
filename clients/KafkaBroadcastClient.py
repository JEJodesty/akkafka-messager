import socket, threading
from kafka import KafkaConsumer, KafkaProducer

producer = KafkaProducer(bootstrap_servers='localhost:9092')
consumer = KafkaConsumer('channelOut')


def send():
    while True:
        msg = raw_input('\nKafka > ')
        producer.send('chat', msg)


def receive():
    for msg in consumer:
        print('\n' + str(msg.value))


if __name__ == "__main__":

    thread_send = threading.Thread(target = send)
    thread_send.start()

    thread_receive = threading.Thread(target = receive)
    thread_receive.start()