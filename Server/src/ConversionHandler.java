import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;


public class ConversionHandler implements Runnable {
	
	private static final int CHUNK_LENGTH = 1024;
	private static final int TIMEOUT = 3*1000;
	
	Socket socket = null;
	DataInputStream inputSocketStream = null;
    DataOutputStream outputSocketStream = null;
    boolean success = true;
    int responseCode = 0;
    String errorMessage = null;
    String typeOrigin = null;
    String typeTarget = null;
    int fileLength;
    
    

	public ConversionHandler(Socket socket) {
		this.socket = socket;
		try {
			this.socket.setSoTimeout(TIMEOUT);
			inputSocketStream = new DataInputStream(socket.getInputStream());
			outputSocketStream = new DataOutputStream(socket.getOutputStream());
		} catch (SocketException e) {
			errorMessage = new String("Error in setting the timeout for the socket.");
			success = false;
			responseCode = 2;
			e.printStackTrace();
		} catch (IOException e) {
			errorMessage = new String("Error in accessing the socket I/O streams.");
			success = false;
			responseCode = 2;
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		
	  if (success)
		// read the metadata from the client
		try {
			
			//read the original type of the file
			byte[] typeArray = new byte[3];
			int totalReadBytes = 0;
			while(totalReadBytes < 3) {
				int readBytes = inputSocketStream.read(typeArray, totalReadBytes, 3-totalReadBytes);
				totalReadBytes += readBytes;
			}
			typeOrigin = new String(typeArray, StandardCharsets.US_ASCII);
			
			//read the target type of the file
			typeArray = new byte[3];
			totalReadBytes = 0;
			while(totalReadBytes < 3) {
				int readBytes = inputSocketStream.read(typeArray, totalReadBytes, 3-totalReadBytes);
				totalReadBytes += readBytes;
			}
			typeTarget = new String(typeArray, StandardCharsets.US_ASCII);
			
			System.out.println("The information about the media types has been received.");
			
			//check that the media types are supported
			if((!typeOrigin.equalsIgnoreCase("png") && !typeOrigin.equalsIgnoreCase("jpg")
 	        	 && !typeOrigin.equalsIgnoreCase("gif")) || (!typeTarget.equalsIgnoreCase("png") 
 	        	 && !typeTarget.toString().equalsIgnoreCase("jpg") && !typeTarget.equalsIgnoreCase("gif") )) 
			{
 	        	success = false;
 	        	errorMessage = new String("Media types not supported.");
 	        	responseCode = 1;
 	        }

		} catch (SocketException e) {
			success = false;
			errorMessage = new String("Timeout expired for reading the medatadata.");
			responseCode = 1;
			e.printStackTrace();
		}  catch (IOException e) {
			success = false;
			errorMessage = new String("Error in receiving the metadata.");
			responseCode = 2;
			e.printStackTrace();
		}
		
		//read the file length sent by the client
		if(success) {
			try {
				fileLength = inputSocketStream.readInt();
				System.out.println("The information about the file length has been received.");
			} catch (SocketException e) {
				success = false;
				errorMessage = new String("Timeout expired for receiving the file length.");
				responseCode = 1;
				e.printStackTrace();
			}  catch (IOException e) {
				success = false;
				errorMessage = new String("Error in receiving the file length.");
				responseCode = 2;
				e.printStackTrace();
			}
		}
		
		//read the file sent by the client
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		if(success) {
				try {
					byte[] fileArray = new byte[CHUNK_LENGTH];
					int bytesToRead = fileLength;
					
					//read file chunks, until less than CHUNK_LENGTH bytes remain
					while(fileLength > CHUNK_LENGTH) {
						int readBytes = inputSocketStream.read(fileArray, 0, CHUNK_LENGTH);
						baos.write(fileArray, 0, readBytes);
						bytesToRead -= readBytes;
						fileLength=bytesToRead;
						fileArray = new byte[CHUNK_LENGTH];
					}
					//read last chunk
					while(bytesToRead > 0) {
						int readBytes = inputSocketStream.read(fileArray, 0, bytesToRead);
						baos.write(fileArray, 0, readBytes);
						bytesToRead -= readBytes;
						fileArray = new byte[CHUNK_LENGTH];
					}
					
					System.out.println("The file has been received.");
					
				} catch (SocketException e) {
					success = false;
					errorMessage = new String("Timeout expired for receiving the file.");
					responseCode = 1;
					e.printStackTrace();
				}	catch (IOException e) {
					success = false;
					errorMessage = new String("Error in receiving the file.");
					responseCode = 2;
					e.printStackTrace();
				}
				
		}
		
		//conversion of the file
		ByteArrayOutputStream baosImageToSend = new ByteArrayOutputStream();
		if(success) {
			try {
				byte[] bytes = baos.toByteArray();
			    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
				BufferedImage imageReceived;
				imageReceived = ImageIO.read(bais);
				ImageIO.write(imageReceived, typeTarget.toString().toLowerCase(), baosImageToSend); 
				System.out.println("The file has been converted.");
			} catch (IOException e) {
				success = false;
				errorMessage = new String("Error during the image conversion.");
				responseCode = 2;
				e.printStackTrace();
			}
		}
		
		//send back the response to the client
		//case 0: success
		if(success) {
			try {
				//send the success code ('0')
				outputSocketStream.write('0');
				
				//send the length of the converted file
				int convertedLength = baosImageToSend.size();
				outputSocketStream.writeInt(convertedLength);
				
				//send the converted file
				BufferedInputStream bisImageToSend = new BufferedInputStream(new ByteArrayInputStream(baosImageToSend.toByteArray()));
				byte[] buffer = new byte[CHUNK_LENGTH];
	            int bytesToWrite;
				while ((bytesToWrite = bisImageToSend.read(buffer, 0, CHUNK_LENGTH)) != -1) {
					  outputSocketStream.write(buffer, 0, bytesToWrite);
					  buffer = new byte[CHUNK_LENGTH];
				}
				
				System.out.println("The converted file has been sent back.");
				
			} catch (SocketException e) {
				errorMessage = new String("Error in socket management while sending the positive response.");
				e.printStackTrace();
			} catch (IOException e) {
				errorMessage = new String("Error in sending the positive response.");
				e.printStackTrace();
			}
		} 
		//case 2: error
		else {
			
			try {
				//send the error code
				if(responseCode == 1)
					outputSocketStream.write('1');
				else if(responseCode == 2)
					outputSocketStream.write('2');
				
				//send the length of the error message
				int messageLength = errorMessage.length();
				outputSocketStream.writeInt(messageLength);
				
				//send the error message
				BufferedInputStream bisMessageToSend = new BufferedInputStream(new ByteArrayInputStream(errorMessage.getBytes()));
				byte[] buffer = new byte[CHUNK_LENGTH];
	            int bytesToWrite;
				while ((bytesToWrite = bisMessageToSend.read(buffer, 0, CHUNK_LENGTH)) != -1) {
					  outputSocketStream.write(buffer, 0, bytesToWrite);
					  buffer = new byte[CHUNK_LENGTH];
				}
				
				System.out.println("Error: " + errorMessage);
				System.out.println("The error message has been sent back.");
				
			} catch (SocketException e) {
				errorMessage = new String("Error in socket management while sending the negative response.");
				e.printStackTrace();
			} catch (IOException e) {
				errorMessage = new String("Error in sending the negative response.");
				e.printStackTrace();
			}
			
		}
		
		
		try {
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		} 

		
	}

}
