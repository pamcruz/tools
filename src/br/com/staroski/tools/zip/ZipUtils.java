package br.com.staroski.tools.zip;

import static br.com.staroski.tools.io.IO.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Classe utilit�ria para compacta��o e descompacta��o de arquivos ZIP
 * 
 * @author Ricardo Artur Staroski
 */
public final class ZipUtils {

	/**
	 * Compacta determindado arquivo ou diret�rio para o arquivo ZIP
	 * especificado
	 * 
	 * @param input
	 *            O arquivo ou diret�rio de entrada
	 * @param output
	 *            O arquivo ZIP de sa�da
	 */
	public static void compress(final File input, final File output) throws IOException {
		if (!input.exists()) {
			throw new IOException(input.getName() + " n�o existe!");
		}
		if (output.exists()) {
			if (output.isDirectory()) {
				throw new IllegalArgumentException("\"" + output.getAbsolutePath() + "\" n�o � um arquivo!");
			}
		} else {
			final File parent = output.getParentFile();
			if (parent != null) {
				parent.mkdirs();
			}
			output.createNewFile();
		}
		final ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output));
		zip.setLevel(Deflater.BEST_COMPRESSION);
		compress("", input, zip);
		zip.finish();
		zip.flush();
		zip.close();
	}

	/**
	 * Extrai um arquivo ZIP para o diret�rio especificado
	 * 
	 * @param input
	 *            O arquivo ZIP de entrada
	 * @param output
	 *            O diret�rio de sa�da
	 */
	public static void extract(final File input, final File output) throws IOException {
		if (input.exists()) {
			if (input.isDirectory()) {
				throw new IllegalArgumentException("\"" + input.getAbsolutePath() + "\" n�o � um arquivo!");
			}
		} else {
			throw new IllegalArgumentException("\"" + input.getAbsolutePath() + "\" n�o existe!");
		}
		if (output.exists()) {
			if (output.isFile()) {
				throw new IllegalArgumentException("\"" + output.getAbsolutePath() + "\" n�o � um diret�rio!");
			}
		}
		final ZipFile zip = new ZipFile(input);
		extract(zip, output);
		zip.close();
	}

	// Adiciona determinado arquivo ao ZIP
	private static void compress(final String caminho, final File arquivo, final ZipOutputStream saida) throws IOException {
		final boolean dir = arquivo.isDirectory();
		final String nome = arquivo.getName();
		final ZipEntry elemento = new ZipEntry(caminho + "/" + nome + (dir ? "/" : ""));
		elemento.setSize(arquivo.length());
		elemento.setTime(arquivo.lastModified());
		saida.putNextEntry(elemento);
		if (dir) {
			final File[] arquivos = arquivo.listFiles();
			for (int i = 0; i < arquivos.length; i++) {
				// recursivamente adiciona outro arquivo ao ZIP
				compress(caminho + "/" + nome, arquivos[i], saida);
			}
		} else {
			final FileInputStream entrada = new FileInputStream(arquivo);
			copy(entrada, saida);
			entrada.close();
		}
	}

	// Retira determinado elemento do arquivo ZIP
	private static void extract(final ZipFile zip, final File pasta) throws IOException {
		InputStream entrada = null;
		OutputStream saida = null;
		String nome = null;
		File arquivo = null;
		ZipEntry elemento = null;
		final Enumeration<?> elementos = zip.entries();
		while (elementos.hasMoreElements()) {
			elemento = (ZipEntry) elementos.nextElement();
			nome = elemento.getName();
			nome = nome.replace('/', File.separatorChar);
			nome = nome.replace('\\', File.separatorChar);
			arquivo = new File(pasta, nome);
			if (elemento.isDirectory()) {
				arquivo.mkdirs();
			} else {
				if (!arquivo.exists()) {
					final File parent = arquivo.getParentFile();
					if (parent != null) {
						parent.mkdirs();
					}
					arquivo.createNewFile();
				}
				saida = new FileOutputStream(arquivo);
				entrada = zip.getInputStream(elemento);
				copy(entrada, saida);
				saida.flush();
				saida.close();
				entrada.close();
			}
			arquivo.setLastModified(elemento.getTime());
		}
	}

	// Construtor privado - N�o h� raz�o em instanciar esta classe
	private ZipUtils() {
	}
}