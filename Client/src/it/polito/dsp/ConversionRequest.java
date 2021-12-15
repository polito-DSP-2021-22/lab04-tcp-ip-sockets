package it.polito.dsp;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ConversionRequest {
	private static final int CHUNK_LENGTH = 1024;
	private static final int TIMEOUT = 30*1000;
	private Socket socket;
	private DataOutputStream outputSocketStream = null; 
	private DataInputStream inputSocketStream = null;
	
	private ConversionRequest(InetAddress serverAddress, int serverPort) throws IOException  {
			this.socket = new Socket(serverAddress, serverPort);
			inputSocketStream = new DataInputStream(socket.getInputStream());
		    outputSocketStream = new DataOutputStream(socket.getOutputStream()); 
			socket.setSoTimeout(TIMEOUT);	        
	}
	 
	public static void main(String[] args)  {
		if (args.length!=3) {
			System.err.println("Check command line arguments: input, output, filename");
			System.exit(1);
		}
		if ( !args[0].matches("[A-Z]{3}") || !args[1].matches("[A-Z]{3}") ) {
			System.err.println("Input and output must be 3 uppercase letters each.");
			System.exit(1);
		}
			
			
		ConversionRequest client = null;
		String input = args[0];
		String output = args[1];
		String filename = args[2];
		try {
			client = new ConversionRequest(
			        InetAddress.getByName("0.0.0.0"), 
			        2001);
			System.out.println("Input: "+input+" Output: "+output+" Filename: "+filename);
	        System.out.println("\r\nConnected to Server: " + client.socket.getInetAddress());
	      
		} catch (Exception e) {
			System.err.println("Error when connecting to server");
			e.printStackTrace();
			System.exit(1);
		}
		
		try {
			client.sendCommands(input,output,filename);
		} catch (IOException e) {
			System.out.println("Cannot connect to server");
			e.printStackTrace();
		} 
    }

	private void sendCommands(String input, String output, String filename) throws IOException {
		 // prepare input stream
		 InputStream is = socket.getInputStream();
		 File file = new File("image/"+filename);
		 int fileSizeToSend = (int) file.length();
		 int fileSizeToRead=0;
         FileInputStream fin = new FileInputStream("image/"+filename);
         int count=0;
         byte [] fileByteArray  = new byte [CHUNK_LENGTH];
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
		
         // sending input format
         byte[] inputb = input.getBytes(StandardCharsets.US_ASCII);
		 outputSocketStream.write(inputb);
		 // sending output format =
		 byte[] outputb = output.getBytes(StandardCharsets.US_ASCII);
		 outputSocketStream.write(outputb);
		 
		 System.out.println("The information about the media types has been sent.");
			 
         // sending file size
		 outputSocketStream.writeInt(fileSizeToSend);
		 System.out.println("The information about the file length has been sent.");
		 
		 
         // sending the File itself
         while ((count = fin.read(fileByteArray)) > 0)
        	  outputSocketStream.write(fileByteArray, 0, count);
         // outputSocketStream.write(fileByteArray);
         outputSocketStream.flush();
         fin.close();
         System.out.println("The file has been sent.");
        
         // reading the feedback string
         char isSuccess  = (char)is.read();
         System.out.println("The response code has been received.");
         
         switch(isSuccess) {
         case '0':
        	 fileSizeToRead = inputSocketStream.readInt();
			 int bytesToRead = fileSizeToRead;
			 
			 
			//read file chunks, until less than CHUNK_LENGTH bytes remain
			 while(fileSizeToRead > CHUNK_LENGTH) {
				// System.out.println(fileSizeToRead);
				int readBytes = inputSocketStream.read(fileByteArray, 0, CHUNK_LENGTH);
				baos.write(fileByteArray, 0, readBytes);
				bytesToRead -= readBytes;
				fileSizeToRead=bytesToRead;
				fileByteArray = new byte[CHUNK_LENGTH];
			 }
			//read last chunk
			while(bytesToRead > 0) {
				int readBytes = inputSocketStream.read(fileByteArray, 0, bytesToRead);
				baos.write(fileByteArray, 0, readBytes);
				bytesToRead -= readBytes;
				fileByteArray = new byte[CHUNK_LENGTH];
			}			 
			try(OutputStream outputStream = new FileOutputStream("image/output."+output.toLowerCase())) {
				baos.writeTo(outputStream);
				outputStream.close();
			}
			System.out.println("The converted file has been received.");
        	break;
         case '1':
        	 System.out.println("Wrong Request");
        	 break;
         case '2':
        	 System.out.println("Internal Server Error");
           break;
       }
		 
	}

}
