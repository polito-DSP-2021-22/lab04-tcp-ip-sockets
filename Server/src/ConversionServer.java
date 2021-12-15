import java.io.IOException;

public class ConversionServer {

	public static void main(String[] args) {
		
		int port = 2001;
		Converter converter = null;
		
		//Creation of the server socket
		try {
			converter = new Converter(port);
		} catch (IOException e) {
			System.out.println("Error in the creation of the socker server.");
			e.printStackTrace();
			System.exit(0);
		}
		
		//Management of the communications with clients
		System.out.println("Server running on port " + port + ".");
		try {
			converter.execute();
		} catch (IOException e) {
			System.out.println("Error in the management of the server socket.");
			e.printStackTrace();
		}
		
		//Closure of the server socket (in case of error)
		try {
			converter.stop();
		} catch (IOException e) {
			System.out.println("Error in the closure of the server socket.");
			e.printStackTrace();
		}

	}

}
