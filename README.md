# Project description

Implementing a simple version of online videoconferencing and messaging application based on a client server model consisting of one server and multiple clients communicating concurrently. The text messages should be communicated using TCP for the reason of reliability, while the video (we will use video files instead of capturing the live video streams from cameras and microphones) should be communicated using UDP for the reason of low latency. application will support a range of functions that are typically found on videoconferencing including authentication, posting text message to all participants or one particular participant, uploading video streams (i.e., files in this assignment). 

# General functionality

The client program will initiate a TCP connection with the server. Upon connection establishment, the user will initiate the authentication process. The client will interact with the user through the command line interface. Following successful authentication, the user will initiate one of the available commands. All commands require a simple request response interaction between the client and server or two clients. The user may execute a series of commands (one after the other) and eventually quit. Both the client and server MUST print meaningful messages at the command prompt that capture the specific interactions taking place.

# detail supported function
## authentication
When a client requests for a connection to the server, e.g., for attending a video conference, the server should prompt the user to input the username and password and authenticate the user. The valid username and password combinations will be stored in a file called credentials.txt which will be in the same directory as the server program.
* If the credentials are correct, the client is considered to be logged in and a welcome message is displayed.
* On entering invalid credentials, the user is prompted to retry. After a number of consecutive failed attempts, the user is blocked for a duration of 10 seconds (number is an integer command line argument supplied to the server and the valid value of number should be between 1 and 5) and cannot login during this 10 second duration (even from another IP address).


Server Command line argument: TCPportNumber maximumloginTryAttempt

Client Command line argument: IPAddress TCPportNumber UDPportNumber
