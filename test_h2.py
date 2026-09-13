import socket
s = socket.socket()
s.connect(('127.0.0.1', 8001))
s.sendall(b'PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n')
print(s.recv(1024))
