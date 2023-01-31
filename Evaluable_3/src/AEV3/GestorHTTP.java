package AEV3;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.xml.bind.DatatypeConverter;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.json.JSONObject;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class GestorHTTP implements HttpHandler {

	MongoCollection<Document> coleccion=null;
	
	

	@Override
	/**
	 * En esta función decidimos y ejecutamos la funcion de GET o POST dependiendo de sus casos, a parte de guardar los logs etc.
	 */
	public void handle(HttpExchange httpExchange) throws IOException {
		String requestParamValue = null;
		int caso = 0;
		Timestamp timestamp = new Timestamp(System.currentTimeMillis());
		conexio();
		guardarLog(httpExchange,timestamp);
		if ("GET".equalsIgnoreCase(httpExchange.getRequestMethod())) {
			caso = asignarCaso(httpExchange);

			if(caso==1) {
				handleGETResponseCaso1(httpExchange);
			}
			if(caso==2) {
				requestParamValue = handleGetRequest(httpExchange);
				handleGETResponseCaso2(httpExchange,requestParamValue);
			}
			if(caso==3) {
				handleGETResponseCaso3(httpExchange);
			}			
		} else if ("POST".equalsIgnoreCase(httpExchange.getRequestMethod())) {
			if(correct(httpExchange)) {
				JSONObject request=handlePostRequest(httpExchange);
				try {
					handlePostResponse(httpExchange, request);
				} catch (IOException | MessagingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}else {
				handlePostResponseError(httpExchange);
			}
		} else {
			System.out.println("DESCONOCIDA");
		}
	}

	/**
	 * Obtiene el areas, splitea después del =, ese dato luego se lo enchufamos a la base de datos
	 * @param httpExchange 
	 * @return
	 */
	private String handleGetRequest(HttpExchange httpExchange) {
		return httpExchange.getRequestURI().toString().split("\\?")[1].split("=")[1];
	}

	/**
	 * Asigna cada caso dependiendo de la petición realizada
	 * @param httpExchange 
	 * @return
	 */
	private int asignarCaso(HttpExchange httpExchange) {
		String caso= httpExchange.getRequestURI().toString().split("/servidor/")[1];
		if(caso.contains("mostrarTodos")) {
			return 1;
		}else if(caso.contains("mostrarUno")){
			return 2;
		}else {
			return 3;
		}
	}

	/**
	 * Realiza una petición GET en la cual recoge la información de los delincuentes, para después mostrarla
	 * @param httpExchange 
	 * @param requestParamValue
	 * @throws IOException
	 */
	private void handleGETResponseCaso2(HttpExchange httpExchange,String requestParamValue) throws IOException {
		Bson query = Filters.eq("alias", requestParamValue);
		MongoCursor<Document> cursor = coleccion.find(query).iterator();
		String alias="Error";
		String nombre="Error";
		String nacionalidad="Error";
		String imag="Error";
		int any=0;
        while (cursor.hasNext()) {
            JSONObject obj = new JSONObject(cursor.next().toJson());
            alias=obj.getString("alias");
            System.out.println("Alias: :"+alias);
            nombre=obj.getString("nombreCompleto");
            any=obj.getInt("fechaNacimiento");
            nacionalidad=obj.getString("nacionalidad");
            imag=obj.getString("imagen");
        }
		
		OutputStream outputStream = httpExchange.getResponseBody();
		StringBuilder htmlBuilder = new StringBuilder();
		htmlBuilder.append("<html>")
				.append("<body>")
				.append("<h1>")
				.append("Criminales de CNI ")
				.append("</h1>")
				.append("<h3> Alias: "+alias)
				.append("</h3>")
				.append("<h3> Nombre: "+nombre)
				.append("</h3>")
				.append("<h3> Fecha: "+any)
				.append("</h3>")
				.append("<h3> Nacionalidad: "+nacionalidad)
				.append("</h3>")
				.append("<img src=\"data:image/png;base64, " + imag + "\" width=\"500\" height=\"600\"> ");
				
		// encode HTML content
		String htmlResponse = htmlBuilder.toString();
		// this line is a must
		httpExchange.sendResponseHeaders(200, htmlResponse.length());
		outputStream.write(htmlResponse.getBytes());
		outputStream.flush();
		outputStream.close();
	}
	
	
	/**
	 * Realiza una petición GET la cual muestra un listado con todos los criminales
	 * @param httpExchange
	 * @throws IOException
	 */
	private void handleGETResponseCaso1(HttpExchange httpExchange) throws IOException {
		
		MongoCursor<Document> cursor = coleccion.find().iterator();
		List<String> alias=new ArrayList<String>();
        while (cursor.hasNext()) {
            JSONObject obj = new JSONObject(cursor.next().toJson());
            alias.add(obj.getString("alias"));
            System.out.println("Alias: :"+obj.getString("alias"));
        }
        //Usamos el string builder porque el string normal nos fallaba
		OutputStream outputStream = httpExchange.getResponseBody();
		StringBuilder htmlBuilder = new StringBuilder();
		htmlBuilder.append("<html>")
				.append("<body>")
				.append("<h1>")
				.append("Lista de Criminales de CNI ")
				.append("</h1>")
				.append("<ul>");
				for(int i=0;i<alias.size();i++) {
					htmlBuilder.append("<li>")
					.append(alias.get(i))
					.append("</li>");
				}
				htmlBuilder.append("</ul>");

		String htmlResponse = htmlBuilder.toString();
		httpExchange.sendResponseHeaders(200, htmlResponse.length());
		outputStream.write(htmlResponse.getBytes());
		outputStream.flush();
		outputStream.close();
	}
	
	/**
	 * Tercer caso, devuelve un mensaje de error correspondiente al 200 
	 * @param httpExchange
	 * @throws IOException
	 */
	private void handleGETResponseCaso3(HttpExchange httpExchange) throws IOException {
		OutputStream outputStream = httpExchange.getResponseBody();
		String htmlResponse = "<html><body><h3>Error: ruta incorrecta</h3></body></html>";
		httpExchange.sendResponseHeaders(200, htmlResponse.length());
		outputStream.write(htmlResponse.getBytes());
		outputStream.flush();
		outputStream.close();
		System.out.println("El servidor devuelve codigo 200");
	}
	
	/**
	 * Conexión con la base de datos en mongoDB
	 */
	public void conexio() {
		String ip="haitian:haitian@3.226.136.98";
		int port=27017;
		MongoClientURI uri= new MongoClientURI("mongodb://"+ip+":"+port);
		MongoClient mongoClient =new MongoClient(uri);
		MongoDatabase database = mongoClient.getDatabase("CNI");
		coleccion = database.getCollection("Criminales");
		if(coleccion!=null) {
			System.out.println("Conectado con exito");
		}else {
			System.out.println("Fallo al conectar");
		}
		

	}
	
	/**
	 * Comprueba si la petición es correcta, en caso de que contenga nuevo para la cadena /servidor/nuevo retorna true y false en caso contrario
	 * @param httpExchange
	 * @return
	 */
	private boolean correct(HttpExchange httpExchange) {
		String caso= httpExchange.getRequestURI().toString().split("/servidor/")[1];
		if(caso.contains("nuevo")) {
			return true;
		}else {
			return false;
		}
	}
	
	/**
	 * Recoge los datos del body y los transforma en un JSON object para pasarselo al response y enviarlo a la base de datos
	 * @param httpExchange
	 * @return
	 */
	private JSONObject handlePostRequest(HttpExchange httpExchange) {
		System.out.println("Recibida URI tipo POST: " + httpExchange.getRequestBody().toString());
		InputStream is = httpExchange.getRequestBody();
		
		BufferedReader bR = new BufferedReader(  new InputStreamReader(is));
		String line = "";

		StringBuilder responseStrBuilder = new StringBuilder();
		try {
			while((line =  bR.readLine()) != null){

			    responseStrBuilder.append(line);
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		try {
			bR.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		JSONObject result= new JSONObject(responseStrBuilder.toString());
		System.out.println(result);
		return result;
	}
	
	/**
	 * Inserta un objeto JSON en la base de datos con los datos rellenados
	 * @param alias
	 * @param nombre
	 * @param fechaNac
	 * @param nacionalidad
	 * @param imag
	 */
	private void insertarRegistro(String alias,String nombre,int fechaNac,String nacionalidad,String imag) {
		Document doc = new Document();
		doc.append("alias", alias);
		doc.append("nombreCompleto", nombre);
		doc.append("fechaNacimiento", fechaNac);
		doc.append("nacionalidad", nacionalidad);
		doc.append("imagen", imag);
		coleccion.insertOne(doc);//coleccion variable global
	}
	
	/**
	 * Realiza una petición POST a la API, guarda la información en la base de datos y envía un correo al usuario seleccionado
	 * @param httpExchange
	 * @param requestParamValue
	 * @throws IOException
	 * @throws MessagingException
	 */
	private void handlePostResponse(HttpExchange httpExchange, JSONObject requestParamValue) throws IOException, MessagingException {
		Scanner teclado = new Scanner(System.in);
		OutputStream outputStream = httpExchange.getResponseBody();
		String htmlResponse = "<html><body><h3>Insertado con exito</h3></body></html>";
		httpExchange.sendResponseHeaders(200, htmlResponse.length());
		outputStream.write(htmlResponse.getBytes());
		outputStream.flush();
		outputStream.close();
		System.out.println("El servidor devuelve codigo 200");
		//Lee el json y recoge los datos
		String alias=(String) requestParamValue.get("alias");
		String nombre = (String) requestParamValue.get("nombreCompleto");
		int fecha=(int)requestParamValue.get("fechaNacimiento");
		String nacionalidad=(String) requestParamValue.get("nacionalidad");
		String imagen=(String) requestParamValue.get("imagen");
		String mensaje = "";
		mensaje += "Alias: " + alias + "\n";
		mensaje += "Nombre: " + nombre + "\n";
		mensaje += "Fecha: " + fecha + "\n";
		mensaje += "Nacionalidad: " + nacionalidad + "\n";
		String ruta = convertirFoto(imagen, "Criminal");
		
		insertarRegistro(alias,nombre,fecha,nacionalidad,imagen);//Hacemos insert en mongo db
		System.out.println("A cual correo lo quieres enviar?");
		String correo = teclado.nextLine();
		JTextField mail = new JTextField();
		String[] correos = {correo};
		try {
			envioMail(mensaje, "adios", correos, ruta);
		} catch (UnsupportedEncodingException e) {
			
			e.printStackTrace();
		}

//        Object[] fields = {
//                "Correo: ", mail,
//        };
//        int opcion = JOptionPane.showConfirmDialog(null,fields,"Enviar a...",JOptionPane.OK_CANCEL_OPTION);
//        if (opcion == JOptionPane.OK_OPTION)
//        {
//            String correo = mail.getText();
//            String[] correos = {correo};
//            try {
//				envioMail("hola", "adios", correos);
//			} catch (UnsupportedEncodingException e) {
//				
//				e.printStackTrace();
//			} catch (MessagingException e) {
//				
//				e.printStackTrace();
//			}
//        }
	}
	
	/**
	 * Devuelve un mensaje de error con el asunto de ruta incorrecta
	 * @param httpExchange
	 * @throws IOException
	 */
	private void handlePostResponseError(HttpExchange httpExchange) throws IOException {
		OutputStream outputStream = httpExchange.getResponseBody();
		String htmlResponse = "<html><body><h3>Error: ruta incorrecta</h3></body></html>";
		httpExchange.sendResponseHeaders(200, htmlResponse.length());
		outputStream.write(htmlResponse.getBytes());
		outputStream.flush();
		outputStream.close();
		System.out.println("El servidor devuelve codigo 200");
	}
	
	/**
	 * Convierte una foto de base64 a tipo File
	 * @param foto64 Foto en base64
	 * @param alias Alias del delicuente
	 * @return
	 * @throws IOException
	 */
	private static String convertirFoto(String foto64, String alias) throws IOException {
		String codigoFoto = foto64.split(",")[1];
		byte[] data = Base64.getDecoder().decode(codigoFoto);
		File archivo = new File("./" + alias + ".jpg");
		FileOutputStream fos = new FileOutputStream(archivo);
		fos.write(data);
		fos.close();
		return archivo.getAbsolutePath();
	}
	
	
	/**
	 * Guarda los logs en un fichero de texto, el cual va rellenando a medida que se realizan las peticiones
	 * @param httpExchange Peticion, para conseguir la IP
	 * @param fecha Fecha y hora a la cual se creo la conexión
	 * @throws IOException
	 */
	public void guardarLog(HttpExchange httpExchange, Timestamp fecha) throws IOException {
        File fichero = new File ("logs.txt");
        InetSocketAddress address = httpExchange.getRemoteAddress();
        SimpleDateFormat sdf3 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        if(fichero.createNewFile()) {
            BufferedWriter bw = new BufferedWriter(new FileWriter(fichero, true));
            bw.write("Ip: "+ address + "   Timestamp: " + fecha + "\n");
            bw.close();
        }else {
            BufferedWriter bw = new BufferedWriter(new FileWriter(fichero, true));
            bw.write("Ip: "+ address + "   Timestamp: " + sdf3.format(fecha) + "\n");
            bw.close();
        }
    }
	
	/**
	 * Se encarga de enviar los correos a los usuarios especificados
	 * @param mensaje Mensaje del correo
	 * @param asunto Asunto del correo
	 * @param email_destino Email de destino
	 * @throws UnsupportedEncodingException 
	 * @throws MessagingException
	 */
	public static void envioMail(String mensaje, String asunto, String[] email_destino, String anexo) throws
			UnsupportedEncodingException, MessagingException {
			Properties props = System.getProperties();
			props.put("mail.smtp.host", "smtp-mail.outlook.com");
			props.put("mail.smtp.user", "cni_los_santos@outlook.es");
			props.put("mail.smtp.clave", "4AVvi7!feJc97^hr");
			props.put("mail.smtp.auth", "true");
			props.put("mail.smtp.starttls.enable", "true");
			props.put("mail.smtp.port", 587);
			Session session = Session.getDefaultInstance(props);
			MimeMessage message = new MimeMessage(session);
			message.setFrom(new InternetAddress("cni_los_santos@outlook.es"));
			message.addRecipients(Message.RecipientType.TO, email_destino[0]);
			message.setSubject(asunto);
			BodyPart messageBodyPart1 = new MimeBodyPart();
			messageBodyPart1.setText(mensaje);
			BodyPart messageBodyPart2 = new MimeBodyPart();
			DataSource src= new FileDataSource(anexo);
			messageBodyPart2.setDataHandler(new DataHandler(src));
			messageBodyPart2.setFileName(anexo);
			Multipart multipart = new MimeMultipart();
			multipart.addBodyPart(messageBodyPart1);
			multipart.addBodyPart(messageBodyPart2);
			message.setContent(multipart);
			Transport transport = session.getTransport("smtp");
			transport.connect("smtp-mail.outlook.com", "cni_los_santos@outlook.es", "4AVvi7!feJc97^hr");
			transport.sendMessage(message, message.getAllRecipients());
			transport.close();
			}
}
