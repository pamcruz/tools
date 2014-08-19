package br.com.staroski.tools.zip;

import static br.com.staroski.tools.io.IO.*;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

/**
 * Classe utilit&aacute;ria para compacta&ccedil;&atilde;o e descompacta&ccedil;&atilde;o de arquivos ZIP
 * 
 * @author Ricardo Artur Staroski
 */
public final class ZipUtils {

	/**
	 * Compacta determindado arquivo ou diret&oacute;rio para o arquivo ZIP
	 * especificado
	 * 
	 * @param input
	 *            O arquivo ou diret&oacute;rio de entrada
	 * @param output
	 *            O arquivo ZIP de sa&iacute;da
	 *
	 *@return O checksum da compacta&ccedil;&atilde;o do arquivo
	 */
	public static long compress(final File input, final File output) throws IOException {
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
		Checksum checksum = createChecksum();
		final ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output));
		zip.setLevel(Deflater.BEST_COMPRESSION);
		compressInternal(null, input, zip, checksum);
		zip.finish();
		zip.flush();
		zip.close();
		return checksum.getValue();
	}

	/**
	 * Extrai um arquivo ZIP para o diret&oacute;rio especificado
	 * 
	 * @param input
	 *            O arquivo ZIP de entrada
	 * @param output
	 *            O diret&oacute;rio de sa&iacute;da
	 *@return O checksum da descompacta&ccedil;&atilde;o do arquivo
	 */
	public static long extract(final File input, final File output) throws IOException {
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
		Checksum checksum = createChecksum();
		final ZipInputStream zip = new ZipInputStream(new FileInputStream(input));
		extractInternal(zip, output, checksum);
		zip.close();
		return checksum.getValue();
	}

	// Adiciona determinado arquivo ao ZIP
	private static void compressInternal(final String caminho, final File arquivo, final ZipOutputStream zip, Checksum checksum) throws IOException {
		final boolean dir = arquivo.isDirectory();
		String nome = arquivo.getName();
		nome = (caminho != null ? caminho + "/" + nome : nome);
		final ZipEntry item = new ZipEntry(nome + (dir ? "/" : ""));
		item.setTime(arquivo.lastModified());
		zip.putNextEntry(item);
		if (dir) {
			zip.closeEntry();
			final File[] arquivos = arquivo.listFiles();
			for (int i = 0; i < arquivos.length; i++) {
				// recursivamente adiciona outro arquivo ao ZIP
				compressInternal(nome, arquivos[i], zip, checksum);
			}
		} else {
			item.setSize(arquivo.length());
			final FileInputStream entrada = new FileInputStream(arquivo);
			copy(entrada, zip, checksum);
			entrada.close();
			zip.closeEntry();
		}
	}

	private static Checksum createChecksum() {
		return new CRC32();
	}

	// Retira determinado elemento do arquivo ZIP
	private static void extractInternal(final ZipInputStream zip, final File pasta, Checksum checksum) throws IOException {
		ZipEntry elemento = null;
		while ((elemento = zip.getNextEntry()) != null) {
			String nome = elemento.getName();
			nome = nome.replace('/', File.separatorChar);
			nome = nome.replace('\\', File.separatorChar);
			File arquivo = new File(pasta, nome);
			if (elemento.isDirectory()) {
				arquivo.mkdirs();
			} else {
				boolean existe = arquivo.exists();
				if (!existe) {
					final File parent = arquivo.getParentFile();
					if (parent != null) {
						parent.mkdirs();
					}
					arquivo.createNewFile();
				}
				boolean oculto = false;
				boolean somenteLeitura = false;
				if (existe) {
					oculto = arquivo.isHidden();
					if (oculto) {
						Files.setAttribute(arquivo.toPath(), "dos:hidden", false);
					}
					somenteLeitura = !arquivo.canWrite();
					if (somenteLeitura) {
						arquivo.setWritable(true);
					}
				}

				OutputStream saida = new FileOutputStream(arquivo);
				copy(zip, saida, checksum);
				saida.close();

				if (existe) {
					if (somenteLeitura) {
						arquivo.setWritable(false);
					}
					if (oculto) {
						Files.setAttribute(arquivo.toPath(), "dos:hidden", true);
					}
				}
			}
			arquivo.setLastModified(elemento.getTime());
		}
	}

	// Construtor privado - N�o h� raz�o em instanciar esta classe
	private ZipUtils() {
	}
}