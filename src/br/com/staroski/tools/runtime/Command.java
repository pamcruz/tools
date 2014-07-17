package br.com.staroski.tools.runtime;

import static br.com.staroski.tools.io.IO.*;

import java.io.*;
import java.util.*;

/**
 * Classe utilit�ria para facilitar a execu��o de linhas de comando.<BR>
 * Possui dois construtores:<BR>
 * - o {@link #CommandLine(String, String...) primeiro} recebe a linha de comando e opcionalmente seus par�metros;<BR>
 * - o {@link #CommandLine(CommandListener, CommandListener, String, String...) segundo} aceita tamb�m dois {@link CommandListener listeners}, um para receber
 * as mensagens da sa�da padr�o do processo e outro para receber as mensagens da sa�da de erro. <BR>
 * 
 * @author Ricardo Artur Staroski
 */
public final class Command {
	// classe utilizada internamente para armazenar as sa�das padr�o e de erro do processo
	private static class EnchainedListener implements CommandListener {

		public final StringBuilder buffer;

		private final CommandListener enchained;

		EnchainedListener(CommandListener listener) {
			buffer = new StringBuilder();
			enchained = listener == null ? NULL : listener;
		}

		@Override
		public void receive(String text) {
			buffer.append(text);
			enchained.receive(text);
		}
	}

	// classe interna utilizada para ler os stream de saida e de erro do processo
	private class StreamReader implements Runnable {

		private final InputStream stream;
		private final CommandListener listener;

		StreamReader(InputStream stream, CommandListener listener) {
			this.stream = stream;
			this.listener = listener;
		}

		@Override
		public void run() {
			try {
				byte[] buffer = new byte[BLOCK_SIZE];
				for (int read = -1; (read = stream.read(buffer)) != -1; listener.receive(new String(buffer, 0, read)))
					;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	};

	private EnchainedListener outputListener;
	private EnchainedListener errorListener;

	private List<String> command;

	private PrintWriter writer;

	/**
	 * Instancia uma nova linha de comando
	 * 
	 * @param exec
	 *            O caminho do execut�vel
	 * @param params
	 *            Opcional, os argumentos do execut�vel
	 */
	public Command(String exec, String... params) {
		outputListener = new EnchainedListener(null);
		errorListener = new EnchainedListener(null);

		command = new LinkedList<String>();
		addParam(exec, params);
	}

	/**
	 * Permite adicionar mais par�metros � linha de comando.
	 * 
	 * @param first
	 *            O primeiro par�metro a ser adicionado.
	 * 
	 * @param others
	 *            O resto dos par�metros a serem adicionados.
	 */
	public void addParam(String first, String... others) {
		command.add(first);
		for (String other : others) {
			command.add(other);
		}
	}

	/**
	 * Permite adicionar mais par�metros � linha de comando.
	 * 
	 * @param params
	 *            Os par�metros a serem adicionados.
	 */
	public void addParam(String[] params) {
		for (String param : params) {
			command.add(param);
		}
	}

	/**
	 * Executa esta linha de comando
	 * 
	 * @return O c�digo de sa�da do processo
	 * @throws IOException
	 */
	public int execute() throws IOException {
		return execute(null);
	}

	/**
	 * Executa esta linha de comando
	 * 
	 * @param directory
	 *            O diret�rio a partir do qual a linha de comando ser� executada
	 * 
	 * @return O c�digo de sa�da do processo
	 * @throws IOException
	 */
	public int execute(File directory) throws IOException {
		try {
			Process process = executeAssinchronous(directory);
			return process.waitFor();
		} catch (InterruptedException e) {
			e.printStackTrace();
			return -1;
		}
	}

	/**
	 * Executa esta linha de comando ass�ncronamente, devolvendo o {@link Process processo} disparado, ficando a encardo do desenvolvedor invocar o m�todo
	 * {@link Process#waitFor() waitFor()} para obter o c�digo de sa�da da aplica��o
	 * 
	 * 
	 * @return O {@link Process processo} que foi disparado
	 * @throws IOException
	 */
	public Process executeAssinchronous() throws IOException {
		return executeAssinchronous(null);
	}

	/**
	 * Executa esta linha de comando ass�ncronamente, devolvendo o {@link Process processo} disparado, ficando a encardo do desenvolvedor invocar o m�todo
	 * {@link Process#waitFor() waitFor()} para obter o c�digo de sa�da da aplica��o
	 * 
	 * @param directory
	 *            O diret�rio a partir do qual a linha de comando ser� executada
	 * 
	 * @return O {@link Process processo} que foi disparado
	 * @throws IOException
	 */
	public Process executeAssinchronous(File directory) throws IOException {
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.directory(directory);
		Process process = builder.start();

		Thread errorsReader = new Thread(new StreamReader(process.getErrorStream(), errorListener));
		Thread outputReader = new Thread(new StreamReader(process.getInputStream(), outputListener));

		writer = new PrintWriter(new BufferedOutputStream(process.getOutputStream()));

		errorsReader.start();
		outputReader.start();

		return process;
	}

	/**
	 * @return o texto contendo as mensagens de erro do processo
	 */
	public String getError() {
		return errorListener.buffer.toString();
	}

	/**
	 * Obt�m o {@link PrintWriter PrintWriter} para se escrever na entrada do processo.<BR>
	 * S� faz sentido utilizar o {@link PrintWriter PrintWriter} para processos que foram inicializados atrav�s dos m�todos {@link #executeAssinchronous()} ou {@link #executeAssinchronous(File)}
	 * 
	 * @return O objeto {@link PrintWriter PrintWriter} para se escrever na entrada do processo.
	 * 
	 * @throws IllegalStateException se o processo ainda n�o foi inicializado.
	 */
	public PrintWriter getInput() {
		if (writer == null) {
			throw new IllegalStateException("Process not yet started!");
		}
		return writer;
	}

	/**
	 * @return o texto contendo as mensagens de sa�da do processo
	 */
	public String getOutput() {
		return outputListener.buffer.toString();
	}

	/**
	 * Verifica se h� mensagens de erro do processo
	 * 
	 * @return <code>true</code> se houver mensagem de erro <code>false</code> caso contr�rio
	 */
	public boolean hasError() {
		return !getError().isEmpty();
	}

	/**
	 * Verifica se h� mensagens de sa�da do processo
	 * 
	 * @return <code>true</code> se houver mensagem de sa�da <code>false</code> caso contr�rio
	 */
	public boolean hasOutput() {
		return !getOutput().isEmpty();
	}

	/**
	 * @param error
	 *            O objeto que recebera a sa�da de erro do processo
	 */
	public void setErrorListener(CommandListener error) {
		errorListener = new EnchainedListener(error);
	}

	/**
	 * @param output
	 *            O objeto que recebera a sa�da padr�o do processo
	 */
	public void setOutputListener(CommandListener output) {
		outputListener = new EnchainedListener(output);
	}

	/**
	 * Obt�m o texto da linha de comando encapsulada
	 * 
	 * @return O texto da linha de comando encapsulada
	 */
	@Override
	public String toString() {
		StringBuilder text = new StringBuilder();
		for (int i = 0, n = command.size(); i < n; i++) {
			if (i > 0) {
				text.append(" ");
			}
			text.append(command.get(i));
		}
		return text.toString();
	}
}