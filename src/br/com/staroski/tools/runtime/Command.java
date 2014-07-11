package br.com.staroski.tools.runtime;


import static br.com.staroski.tools.io.IO.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Classe utilit�ria para facilitar a execu��o de linhas de comando.<BR>
 * Possui dois construtores:<BR>
 * - o {@link #CommandLine(String, String...) primeiro} recebe a linha de comando e opcionalmente seus par�metros;<BR>
 * - o {@link #CommandLine(CommandListener, CommandListener, String, String...) segundo} aceita tamb�m dois {@link CommandListener listeners}, um para receber
 * as mensagens da sa�da padr�o do processo e outro para receber as mensagens da sa�da de erro. <BR>
 * 
 * @author Ricardo Artur Staroski
 */
final class Command {

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

	private final EnchainedListener outputListener;
	private final EnchainedListener errorListener;

	private String[] command;

	/**
	 * Instancia uma nova linha de comando
	 * 
	 * @param output
	 *            O objeto que recebera a sa�da padr�o do processo
	 * 
	 * @param error
	 *            O objeto que recebera a sa�da de erro do processo
	 * 
	 * @param exec
	 *            O caminho do execut�vel
	 * @param params
	 *            Opcional, os argumentos do execut�vel
	 */
	public Command(CommandListener output, CommandListener error, String exec, String... params) {
		outputListener = new EnchainedListener(output);
		errorListener = new EnchainedListener(error);

		int args = 1 + params.length;
		command = new String[args];
		command[0] = exec;
		for (int i = 1; i < args; i++) {
			command[i] = params[i - 1];
		}
	}

	/**
	 * Instancia uma nova linha de comando
	 * 
	 * @param exec
	 *            O caminho do execut�vel
	 * @param params
	 *            Opcional, os argumentos do execut�vel
	 */
	public Command(String exec, String... params) {
		this(null, null, exec, params);
	}

	/**
	 * Permite adicionar mais par�metros � linha de comando.
	 * 
	 * @param params
	 *            Os par�metros a serem adicionados.
	 */
	public synchronized void addParam(String... params) {
		int addCount = params.length;
		if (addCount > 0) {
			int oldCount = command.length;
			String[] newCommand = new String[oldCount + addCount];
			System.arraycopy(command, 0, newCommand, 0, oldCount);
			System.arraycopy(params, 0, newCommand, oldCount, addCount);
			command = newCommand;
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
	 * Obt�m o texto da linha de comando encapsulada
	 * 
	 * @return O texto da linha de comando encapsulada
	 */
	@Override
	public String toString() {
		StringBuilder text = new StringBuilder();
		for (int i = 0, n = command.length; i < n; i++) {
			if (i > 0) {
				text.append(" ");
			}
			text.append(command[i]);
		}
		return text.toString();
	}
}