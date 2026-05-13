package br.com.pereiraeng.measurements.sto;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import br.com.pereiraeng.core.Flow;
import br.com.pereiraeng.math.timeseries.unit.Med;

/**
 * Classe das funções que manipulam os arquivos que contém dados de medições
 * notáveis de um dado período
 * 
 * @author Philipe PEREIRA
 *
 */
public class EspMed {

	// ------------ FORMATO DO ARQUIVO - POSIÇÕES ------------

	public static final int POS_ATUALIZACAO = 23;

	/**
	 * Número de bytes que tem a data do cabeçalho
	 */
	private static final int POS_DATA_HEADER = 8;

	/**
	 * Número de bytes da tag e suas informações
	 */
	public static final int POS_TAG_INFO = 56;

	/**
	 * Posição do nome da tag dentro do bloco {@link #POS_TAG_INFO}
	 */
	public static final int POS_TAG = 25;

	/**
	 * Tamanho máximo do tamanho de uma tag
	 */
	private static final int TAG_SIZE = POS_TAG_INFO - POS_TAG;

	// ============================================================

	/**
	 * 
	 * @param flow
	 * @param files
	 * @param begin
	 * @param end
	 * @param tags
	 */
	public static void get(Flow<Med> flow, List<File> files, Calendar begin, Calendar end, String[] tags) {
		Med[] out = new Med[tags.length];
		for (File f : files) {
			InputStream is = null;
			try {
				is = new FileInputStream(f);
			} catch (FileNotFoundException | NullPointerException e1) {
				continue;
			}
			DataInputStream dis = new DataInputStream(is);

			try {
				// número de registros
				byte[] b = new byte[4];

				dis.read(b);
				int periods = HistMed.readInt(b);

				dis.read(b);
				int registros = HistMed.readInt(b);

				// ultima atualização
				dis.skip(POS_ATUALIZACAO);
				int bytes = dis.readByte();

				// tabela de dispersão que associa para cada posição da tag no arquivo a posição
				// dele no vetor de entrada desta função
				TreeMap<Integer, Integer> posFile2ch = new TreeMap<>();

				b = new byte[POS_TAG_INFO];
				// procura o número do registro procurado
				for (int l = 0; l < registros; l++) {
					if (posFile2ch.size() < tags.length) {
						// se ainda não achou todo mundo, continua
						dis.read(b);
						String t = new String(b, POS_TAG, TAG_SIZE).trim();

						for (int i = 0; i < tags.length; i++) {
							if (t.equalsIgnoreCase(tags[i])) {
								posFile2ch.put(l, i);
								break;
							}
						}
					} else // se já achou todo mundo, vai pro final
						dis.skip(POS_TAG_INFO);
				}

				if (posFile2ch.size() == 0) {
					dis.close();
					return;
				}

				b = new byte[bytes];

				// cabeçalho indicando a data e hora
				byte[] bh = new byte[POS_DATA_HEADER];
				// cada registro tem 9 bytes a cada minuto
				int dadosDoMinuto = bytes * registros;
				// tamanho do bloco do minuto
				int blocoLength = 2 * POS_DATA_HEADER + dadosDoMinuto;
				// número de blocos para cada instante de tempo
				if ((dis.available() / blocoLength) != periods) {
					dis.close();
					throw new IllegalArgumentException("O número de períodos é diferente do indicado.");
				}

				// para todos os períodos do arquivo
				for (int l = 0; l < periods; l++) {
					// data
					Calendar[] cs = new Calendar[2];

					dis.read(bh);
					long d = ByteBuffer.wrap(bh).order(HistMed.ORDER).getLong();
					Calendar c = Calendar.getInstance();
					c.setTimeInMillis(d);
					cs[0] = c;

					dis.read(bh);
					d = ByteBuffer.wrap(bh).order(HistMed.ORDER).getLong();
					c = Calendar.getInstance();
					c.setTimeInMillis(d);
					cs[1] = c;

					if (!cs[0].after(end) && !cs[1].before(begin)) {
						Iterator<Entry<Integer, Integer>> ite = posFile2ch.entrySet().iterator();
						Entry<Integer, Integer> e = ite.next();
						int posFile = e.getKey();
						for (int k = 0; k < registros; k++) {
							if (k != posFile) {
								// pula os registros que não nos interessam
								dis.skip(bytes);
							} else {
								// lê-se o que nos interessa
								dis.read(b);

								byte[] b1 = Arrays.copyOfRange(b, 0, 8);
								d = ByteBuffer.wrap(b1).order(HistMed.ORDER).getLong();
								float v = Float.NaN;
								if (d != 0L) {
									c = Calendar.getInstance();
									c.setTimeInMillis(d);

									b1 = Arrays.copyOfRange(b, 8, 12);
									v = ByteBuffer.wrap(b1).order(HistMed.ORDER).getFloat();
								}

								int pos = e.getValue();
								if (out[pos] == null ? true : v > out[pos].getValue())
									out[pos] = new Med(c, v);

								// procura-se o próximo
								if (ite.hasNext()) {
									e = ite.next();
									posFile = e.getKey();
								} else
									e = null;
							}
						}
					} else
						dis.skip(dadosDoMinuto);
				}

				if (dis != null)
					dis.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		for (int i = 0; i < out.length; i++) {
			if (out[i] != null) {
				out[i].setChannel(i);
				flow.incomingData(out[i]);
			}
		}
	}

	/**
	 * Função que lê os dados de valores notáveis de um arquivo .ESP
	 * 
	 * @param file   arquivo .ESP com os registros
	 * @param limits lista a ser preenchida com os períodos de tempo. O primeiro
	 *               índice designa os diferentes instantes de tempo, enquanto que o
	 *               segundo alterna entre 0 (instante inicial) e 1 (instante final)
	 * @param meds   lista a ser preenchida com os vetores das medições notáveis. O
	 *               primeiro índice designam os diferentes instantes de tempo,
	 *               enquanto que o segundo designam os diferentes pontos de medição
	 * @param tags   designação dos valores notáveis
	 */
	public static void get(File file, List<Calendar[]> limits, List<Med[]> meds, String... tags) {
		InputStream is = null;
		try {
			is = new FileInputStream(file);
		} catch (FileNotFoundException | NullPointerException e) {
			e.printStackTrace();
		}
		DataInputStream dis = new DataInputStream(is);

		try {
			// número de registros
			byte[] b = new byte[4];

			dis.read(b);
			int periods = HistMed.readInt(b);

			dis.read(b);
			int registros = HistMed.readInt(b);

			// ultima atualização
			dis.skip(POS_ATUALIZACAO);
			int bytes = dis.readByte();

			// tabela de dispersão que associa para cada posição da tag no
			// arquivo a posição dele no vetor de entrada desta função
			TreeMap<Integer, Integer> posFile2ch = new TreeMap<>();

			b = new byte[POS_TAG_INFO];
			// procura o número do registro procurado
			for (int l = 0; l < registros; l++) {
				if (posFile2ch.size() < tags.length) {
					// se ainda não achou todo mundo, continua
					dis.read(b);
					String t = new String(b, POS_TAG, TAG_SIZE).trim();

					for (int i = 0; i < tags.length; i++) {
						if (t.equalsIgnoreCase(tags[i])) {
							posFile2ch.put(l, i);
							break;
						}
					}
				} else // se já achou todo mundo, vai pro final
					dis.skip(POS_TAG_INFO);
			}

			if (posFile2ch.size() == 0) {
				dis.close();
				return;
			}

			b = new byte[bytes];

			// cabeçalho indicando a data e hora
			byte[] bh = new byte[POS_DATA_HEADER];
			// cada registro tem 9 bytes a cada minuto
			int dadosDoMinuto = bytes * registros;
			// tamanho do bloco do minuto
			int blocoLength = 2 * POS_DATA_HEADER + dadosDoMinuto;
			// número de blocos para cada instante de tempo
			int blocos = dis.available() / blocoLength;
			if (blocos != periods) {
				dis.close();
				throw new IllegalArgumentException("O número de períodos é diferente do indicado.");
			}

			// para todos os períodos do arquivo
			for (int l = 0; l < blocos; l++) {
				// data
				Calendar[] cs = new Calendar[2];

				dis.read(bh);
				long d = ByteBuffer.wrap(bh).order(HistMed.ORDER).getLong();
				Calendar c = Calendar.getInstance();
				c.setTimeInMillis(d);
				cs[0] = c;

				dis.read(bh);
				d = ByteBuffer.wrap(bh).order(HistMed.ORDER).getLong();
				c = Calendar.getInstance();
				c.setTimeInMillis(d);
				cs[1] = c;

				limits.add(cs);

				// dados
				Med[] ms = new Med[tags.length];

				Iterator<Entry<Integer, Integer>> ite = posFile2ch.entrySet().iterator();
				Entry<Integer, Integer> e = ite.next();
				int posFile = e.getKey();
				for (int k = 0; k < registros; k++) {
					if (k != posFile) {
						// pula os registros que não nos interessam
						dis.skip(bytes);
					} else {
						// lê-se o que nos interessa
						dis.read(b);

						byte[] b1 = Arrays.copyOfRange(b, 0, 8);
						d = ByteBuffer.wrap(b1).order(HistMed.ORDER).getLong();
						float v = Float.NaN;
						if (d != 0L) {
							c = Calendar.getInstance();
							c.setTimeInMillis(d);

							b1 = Arrays.copyOfRange(b, 8, 12);
							v = ByteBuffer.wrap(b1).order(HistMed.ORDER).getFloat();
						}

						ms[e.getValue()] = new Med(c, v);

						// procura-se o próximo
						if (ite.hasNext()) {
							e = ite.next();
							posFile = e.getKey();
						} else
							e = null;
					}
				}
				meds.add(ms);
			}

			if (dis != null)
				dis.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Função que escreve os dados de valores notáveis em um arquivo .ESP
	 * 
	 * @param folder pasta de destino do arquivo (os nomes dos arquivos são
	 *               definidos em função da data)
	 * @param tags   designação dos valores notáveis
	 * @param limits lista de períodos de tempo. O primeiro índice designa os
	 *               diferentes instantes de tempo, enquanto que o segundo alterna
	 *               entre 0 (instante inicial) e 1 (instante final)
	 * @param meds   lista de vetores com as medições notáveis. O primeiro índice
	 *               designam os diferentes instantes de tempo, enquanto que o
	 *               segundo designam os diferentes pontos de medição
	 */
	public static void writeFile(String folder, String[] tags, List<Calendar[]> limits, List<Med[]> meds) {
		if (limits.size() != meds.size())
			throw new IllegalArgumentException("A lista de períodos tem um tamanho diferente da de medições");
		if (limits.size() == 0)
			return;

		Calendar begin = limits.get(0)[0], end = limits.get(limits.size() - 1)[1];

		try {
			DataOutputStream dos = new DataOutputStream(
					new FileOutputStream(String.format("%s/%2$ty%2$tm%2$td%3$ty%3$tm%3$td.ESP", folder, begin, end)));

			// 4 bytes para o número de períodos
			HistMed.writeInt(dos, limits.size());

			// 4 bytes para o número de tags
			HistMed.writeInt(dos, tags.length);

			// data de criação do arquivo (23 bytes)
			dos.writeBytes(String.format("%1$td-%1$tb-%1$tY %1$tT.00", Calendar.getInstance()));
			// número de bytes por medição (1 byte)
			dos.write(12); // 8 bytes para o long da data, 4 para o float do valor

			for (int j = 0; j < tags.length; j++)
				dos.writeBytes(String.format("%025d%-30s\t", 0, tags[j]));

			byte[] bt = new byte[8], bv = new byte[4];
			for (int i = 0; i < limits.size(); i++) {
				Calendar[] period = limits.get(i);

				// data e hora de início e fim (16 bytes)
				ByteBuffer.wrap(bt).order(HistMed.ORDER).putLong(period[0].getTimeInMillis());
				dos.write(bt);
				ByteBuffer.wrap(bt).order(HistMed.ORDER).putLong(period[1].getTimeInMillis());
				dos.write(bt);

				// medições
				for (int j = 0; j < tags.length; j++) {
					Med m = meds.get(i)[j];

					if (m != null) {
						ByteBuffer.wrap(bt).order(HistMed.ORDER).putLong(m.getTime().getTimeInMillis());
						dos.write(bt);

						ByteBuffer.wrap(bv).order(HistMed.ORDER).putFloat(m.getValue());
						dos.write(bv);
					} else
						dos.write(new byte[12]);
				}
			}

			dos.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	public static void rewriteFile(File file, String[] tags, LinkedList<Calendar[]> limits, LinkedList<Med[]> meds) {
		if (limits.size() != meds.size())
			throw new IllegalArgumentException("A lista de períodos tem um tamanho diferente da de medições");
		if (limits.size() == 0)
			return;

		try {
			RandomAccessFile raf = new RandomAccessFile(file, "rw");

			// número de registros
			byte[] b = new byte[4];

			raf.read(b);
			int periods = HistMed.readInt(b);

			raf.read(b);
			int registros = HistMed.readInt(b);

			// ultima atualização
			raf.skipBytes(POS_ATUALIZACAO);
			int bytes = raf.readByte();

			// tabela de dispersão que associa para cada posição da tag no arquivo a posição
			// dele no vetor de entrada desta função
			TreeMap<Integer, Integer> posFile2ch = new TreeMap<>();

			b = new byte[POS_TAG_INFO];
			// cada registro tem 9 bytes a cada minuto
			int dadosDoPeriodo = bytes * registros;

			// procura o número do registro procurado
			for (int l = 0; l < registros; l++) {
				if (posFile2ch.size() < tags.length) {
					// se ainda não achou todo mundo, continua
					raf.read(b);
					String t = new String(b, POS_TAG, TAG_SIZE).trim();

					for (int i = 0; i < tags.length; i++) {
						if (t.equalsIgnoreCase(tags[i])) {
							posFile2ch.put(l, i);
							break;
						}
					}
				} else // se já achou todo mundo, vai pro final
					raf.skipBytes(POS_TAG_INFO);
			}

			if (posFile2ch.size() == 0) {
				raf.close();
				return;
			}

			b = new byte[bytes];

			// cabeçalho indicando a data e hora
			byte[] bh = new byte[POS_DATA_HEADER];

			Iterator<Calendar[]> itl = limits.iterator();
			Calendar[] ls = itl.next();
			Iterator<Med[]> itm = meds.iterator();
			Med[] ms = itm.next();

			byte[] bt = new byte[8], bv = new byte[4];
			// para todos os períodos do arquivo
			for (int l = 0; l < periods; l++) {
				// data
				Calendar[] cs = new Calendar[2];

				raf.read(bh);
				long d = ByteBuffer.wrap(bh).order(HistMed.ORDER).getLong();
				Calendar c = Calendar.getInstance();
				c.setTimeInMillis(d);
				cs[0] = c;

				raf.read(bh);
				d = ByteBuffer.wrap(bh).order(HistMed.ORDER).getLong();
				c = Calendar.getInstance();
				c.setTimeInMillis(d);
				cs[1] = c;

				if (Arrays.equals(ls, cs)) {
					Iterator<Entry<Integer, Integer>> ite = posFile2ch.entrySet().iterator();
					Entry<Integer, Integer> e = ite.next();
					int posFile = e.getKey();
					for (int k = 0; k < registros; k++) {
						if (k != posFile) {
							// pula os registros que não nos interessam
							raf.skipBytes(bytes);
						} else {
							// escreve o que nos interessa
							Med m = ms[e.getValue()];

							ByteBuffer.wrap(bt).order(HistMed.ORDER).putLong(m.getTime().getTimeInMillis());
							raf.write(bt);

							ByteBuffer.wrap(bv).order(HistMed.ORDER).putFloat(m.getValue());
							raf.write(bv);

							// procura-se o próximo
							if (ite.hasNext()) {
								e = ite.next();
								posFile = e.getKey();
							} else
								e = null;
						}
					}

					if (itl.hasNext()) {
						ls = itl.next();
						ms = itm.next();
					} else
						break;
				} else
					raf.skipBytes(dadosDoPeriodo);
			}
			raf.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

	}

	public static void esp2xls(String esp, String out) {
		File file = new File(esp);

		// arquivo HIS
		DataInputStream dis = null;
		try {
			dis = new DataInputStream(new FileInputStream(file));
		} catch (FileNotFoundException | NullPointerException e) {
			e.printStackTrace();
		}

		// planilha XLSX
		XSSFWorkbook wb = new XSSFWorkbook();
		XSSFSheet sh = wb.createSheet("Medições");
		XSSFRow row = sh.createRow(0);

		try {
			byte[] b = new byte[4];

			// número de períodos
			dis.read(b);
			int periods = HistMed.readInt(b);

			// número de registros
			dis.read(b);
			int registros = HistMed.readInt(b);

			// ultima atualização
			dis.skip(POS_ATUALIZACAO);
			int bytes = dis.readByte();

			b = new byte[POS_TAG_INFO];
			// procura o número do registro procurado
			for (int l = 0; l < registros; l++) {
				dis.read(b);
				String t = new String(b, POS_TAG, TAG_SIZE).trim();
				row.createCell(2 * l + 2).setCellValue(t);
				sh.addMergedRegion(new CellRangeAddress(0, 0, 2 * l + 2, 2 * l + 3));
			}

			b = new byte[bytes];

			// cabeçalho indicando a data e hora
			byte[] bh = new byte[POS_DATA_HEADER];
			// cada registro tem 9 bytes a cada minuto
			int dadosDoPeriodo = bytes * registros;
			// tamanho do bloco do minuto
			int blocoLength = 2 * POS_DATA_HEADER + dadosDoPeriodo;
			// número de blocos para cada instante de tempo
			int blocos = dis.available() / blocoLength;
			if (blocos != periods) {
				dis.close();
				throw new IllegalArgumentException("O número de períodos é diferente do indicado.");
			}

			// para todos os períodos do arquivo
			for (int l = 0; l < blocos; l++) {
				row = sh.createRow(l + 1);

				Calendar c = Calendar.getInstance();

				dis.read(bh);
				long d = ByteBuffer.wrap(bh).order(HistMed.ORDER).getLong();
				c.setTimeInMillis(d);
				row.createCell(0).setCellValue(c);

				dis.read(bh);
				d = ByteBuffer.wrap(bh).order(HistMed.ORDER).getLong();
				c.setTimeInMillis(d);
				row.createCell(1).setCellValue(c);

				// dados
				for (int k = 0; k < registros; k++) {
					dis.read(b);

					byte[] b1 = Arrays.copyOfRange(b, 0, 8);
					d = ByteBuffer.wrap(b1).order(HistMed.ORDER).getLong();
					if (d != 0L) {
						c.setTimeInMillis(d);
						row.createCell(2 * k + 2).setCellValue(c);

						b1 = Arrays.copyOfRange(b, 8, 12);
						float v = ByteBuffer.wrap(b1).order(HistMed.ORDER).getFloat();
						row.createCell(2 * k + 3).setCellValue(v);
					} else {
						row.createCell(2 * k + 2).setCellValue("?");
						row.createCell(2 * k + 3).setCellValue("?");
					}
				}
			}

			if (dis != null)
				dis.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		// ------------------- GERAR ARQUIVO -------------------

		try {
			FileOutputStream fos = new FileOutputStream(out);
			wb.write(fos);
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
