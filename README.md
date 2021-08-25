# Project description

Implementing a simple version of online videoconferencing and messaging application based on a client server model consisting of one server and multiple clients communicating concurrently. The text messages were communicated using TCP for the reason of reliability, while the video (we will use video files instead of capturing the live video streams from cameras and microphones) was communicated using UDP for the reason of low latency. application supported a range of functions that are typically found on videoconferencing including authentication, posting text message to all participants or one particular participant, uploading video streams.

# General functionality

The client program initiates a TCP connection with the server. Upon connection establishment, the user will initiate the authentication process. The client will interact with the user through the command line interface. Following successful authentication, the user can initiate one of the available commands. All commands require a simple request response interaction between the client and server or two clients. The user can execute a series of commands (one after the other) and eventually quit. Both the client and server print meaningful messages at the command prompt that capture the specific interactions taking place.

# Detail supported function
## Authentication
When a client requests for a connection to the server, e.g., for attending a video conference, the server prompt the user to input the username and password and authenticate the user. The valid username and password combinations will be stored in a file called credentials.txt which will be in the same directory as the server program.
* If the credentials are correct, the client is considered to be logged in and a welcome message is displayed.
* On entering invalid credentials, the user is prompted to retry. After a number of consecutive failed attempts, the user is blocked for a duration of 10 seconds and cannot login during this 10 second duration (even from another IP address).

## Text Message operation
Following successful login, the client displays a message to the user informing them of all available commands and prompting to select one command. The following commands are available. All available commands are showing to the user in the first instance after successful login. If an invalid command is selected, an error message is shown to the user and they will be prompted to select one of the available actions.

* MSG: Post Message -> messagenumber; timestamp; username; message; edited
* DLT: Delete Message -> DLT messagenumber timestamp
* EDT: Edit Message -> EDT messagenunmber timestamp message
* RDM: Read Message -> RDM timestamp
* ATU: Display active users -> ATU
* OUT: Log out -> OUT
* UPD: Upload file -> UPD username filename

# Progragmming language
Java

# How to use
clone the file to the local computer and run the client.java and server.java file. Running the server.java file first to open the server and client.java to initiate the connection between the server. When testing, you can run the server and multiple clients on the same machine on separate terminals. In this case, use 127.0.0.1 (local host) as the server IP address. 

example command for server.java:
* java Server server_port number_of_consecutive_failed_attempts

example command for client.java:
* java Client server_IP server_port client_udp_server_port

The additional argument of client_udp_server_port is for the P2P UDP communication. In UDP P2P communication, one client program (i.e., Audience) acts as UDP server and the other client program (i.e., Presenter) acts as UDP client.

# Example interaction
Yoda termainl and Hans termminal was runned by client.java file and server terminal was runned by server.java file
<img width="746" alt="Screen Shot 2021-08-25 at 14 03 21" src="https://user-images.githubusercontent.com/58925650/130724625-d15f32c6-6862-4173-9dd0-55ab7cde19d3.png">


