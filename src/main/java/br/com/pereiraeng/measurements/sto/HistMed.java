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
import java.nio.ByteOrder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import br.com.pereiraeng.math.timeseries.Reg;
import br.com.pereiraeng.math.timeseries.RegP;
import br.com.pereiraeng.math.timeseries.Seq;
import br.com.pereiraeng.math.timeseries.esp.RegS;
import br.com.pereiraeng.math.timeseries.unit.Ct;
import br.com.pereiraeng.math.timeseries.unit.Med;
import br.com.pereiraeng.math.timeseries.unit.Meds;
import br.com.pereiraeng.core.TimeUtils;
import br.com.pereiraeng.core.collections.ArrayUtils;
import br.com.pereiraeng.io.flow.Flow;

/**
 *  * Classe das funções que manipulam os arquivos que contém dados de medições
 *  *  * @author Philipe PEREIRA  *  
 */
public class HistMed {

	public static final String FILENAME_FORMAT = "%s%c%3$ty%3$tm%3$td.HIS";

	public static final ByteOrder ORDER = ByteOrder.LITTLE_ENDIAN;

	// ------------ FORMATO DO ARQUIVO - POSIÇÕES ------------

	public static final int POS_ATUALIZACAO = 23;

	/**
	 * Tamanho do cabeçalho do arquivo (4 bytes do número de medições, 23 bytes da
	 * data de atualização e 1 byte do número de bytes por medição)
	 */
	private static final int HEADER_SIZE = 4 + POS_ATUALIZACAO + 1;

	/**
	 * Número de bytes que tem a data do cabeçalho
	 */
	private static final int POS_DATA_HEADER = 24;

	/**
	 * Número de bytes que tem a data do cabeçalho, mas sem os milissegundos
	 */
	private static final int POS_DATA_HEADER_SEM_MS = POS_DATA_HEADER - 4;

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

	// -------------------------------------------------------------------------

	private static final String TIME_PATTERN = "dd-MMM-yyyy HH:mm:ss";

	/**
	 * Formato de data e hora do cabeçalho do minuto
	 */
	private static final SimpleDateFormat HEADER_BR = new SimpleDateFormat(TIME_PATTERN, new Locale("pt", "BR")),
			HEADER_US = new SimpleDateFormat(TIME_PATTERN, Locale.US);

	private static final String DATE_FORMAT = "%1$td-%1$tb-%1$tY %1$tT.00";

	/**
	 * Número de minutos em um dia
	 */
	private static final int MIN_DAY = 1440;

	public static final byte TEM_ESTIM = 1 << Ct.ESTIM_DISP, ERRO = 1 << Ct.ERRO, FALHA_COM = 1 << Ct.FALHA_COM,
			INIBIDO = 1 << Ct.INIBIDO, SIMULADO = (byte) (1 << Ct.SIMULADO);

	// ======================= PEGAR SOMENTE UMA TAG =======================

	// contínuo

	/**
	 * UMA TAG, CONTÍNUO, FLOW
	 * 
	 * @param flow
	 * @param int2file
	 * @param tag
	 * @param begin
	 * @param end
	 * @param estimated
	 * @param freq
	 */
	public static void get(Flow<Med> flow, Map<Integer, File> int2file, String tag, Calendar begin, Calendar end,
			boolean estimated, int freq) {
		Calendar r = (Calendar) begin.clone();

		// para todos os dias do período
		while (r.before(end)) {
			File f = int2file.get(TimeUtils.date2int(r));

			InputStream is = null;
			try {
				is = new FileInputStream(f);
			} catch (FileNotFoundException | NullPointerException e1) {
				// se o arquivo não for encontrado, pula um dia
				r.add(Calendar.DAY_OF_MONTH, 1);
				r.set(Calendar.HOUR_OF_DAY, 0);
				r.set(Calendar.MINUTE, 0);
				continue;
			}
			DataInputStream dis = new DataInputStream(is);

			try {
				// número de registros
				byte[] b = new byte[4];
				dis.read(b);
				int registros = readInt(b);

				// ultima atualização (23) E número de bytes por medição
				// (normalmente '9')
				dis.skip(POS_ATUALIZACAO);

				int bytes = dis.readByte();

				// procura o número do registro procurado
				int numRegHist = -1;

				b = new byte[POS_TAG_INFO];
				for (int l = 0; l < registros; l++) {
					if (numRegHist < 0) {
						// se ainda não achou, continua
						dis.read(b);
						String t = new String(b, POS_TAG, TAG_SIZE).trim();

						if (t.equalsIgnoreCase(tag.trim()))
							numRegHist = l;
					} else // se já achou
						dis.skip(POS_TAG_INFO);
				}

				// se o registro não for encontrado
				if (numRegHist < 0) {
					try {
						dis.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					// pula um dia
					r.add(Calendar.DAY_OF_MONTH, 1);
					r.set(Calendar.HOUR_OF_DAY, 0);
					r.set(Calendar.MINUTE, 0);
					continue;
				}
				b = new byte[bytes];

				// cabeçalho indicando a data e hora
				byte[] bh = new byte[POS_DATA_HEADER];
				// cada registro tem 9 bytes a cada minuto
				int dadosDoMinuto = bytes * registros;
				// tamanho do bloco do minuto
				int blocoLength = POS_DATA_HEADER + dadosDoMinuto;
				// número de blocos para cada instante de tempo
				int blocos = dis.available() / blocoLength;
				// número de blocos a serem pulados
				int amostra = 1;
				if (freq > 0) {
					// espaço de tempo entre blocos
					int intervalo = MIN_DAY / blocos;
					amostra = (int) Math.ceil(((double) freq) / intervalo);
				}

				// para todos os minutos do dia
				for (int l = 0; l < blocos; l++) {
					// taxa de amostragem
					if (l % amostra == 0) {
						// data
						dis.read(bh);
						String data = new String(bh, 0, POS_DATA_HEADER_SEM_MS);

						// leitura do instante de tempo das medições
						Calendar c = null;
						try {
							c = TimeUtils.date2Calendar(getDate(data));
						} catch (ParseException e) {
							System.err.println("Formato de data não reconhecido (1): " + data + " no arquivo " + f);
						}

						// se estiver dentro do período de tempo procurado,
						// lê-se, senão pula
						// begin <= c < end
						if (c != null ? !c.before(begin) && c.before(end) : false) {
							// dados
							for (int k = 0; k < registros; k++) {
								if (k != numRegHist) {
									// pula os registros que não nos interessam
									dis.skip(bytes);
								} else {
									// lê-se o que nos interessa

									dis.read(b);

									// **** GRANDEZA ****
									byte[] b1 = Arrays.copyOfRange(b, 0, 4);

									float v = ByteBuffer.wrap(b1).order(ORDER).getFloat();

									flow.incomingData(new Med(c, v));

									// se forem pedidos os valores estimados
									if (estimated) {
										byte mascara = b[8];
										// se na máscara disserem que há o
										// valor estimado
										if ((TEM_ESTIM & mascara) != 0) {
											// **** GRANDEZA ESTIMADA ****

											b1 = Arrays.copyOfRange(b, 4, 8);

											v = ByteBuffer.wrap(b1).order(ORDER).getFloat();

											Med m = new Med(c, v);
											m.setEstimated();
											flow.incomingData(m);
										}
									}
								}
							}
						} else
							dis.skip(dadosDoMinuto);
					} else
						dis.skip(blocoLength);
				}

				if (dis != null)
					dis.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			// próximo dia
			r.add(Calendar.DAY_OF_MONTH, 1);
			r.set(Calendar.HOUR_OF_DAY, 0);
			r.set(Calendar.MINUTE, 0);
		}
	}

	/**
	 * UMA TAG, CONTÍNUO, REG
	 * 
	 * @param tag
	 * @param int2file
	 * @param begin
	 * @param end
	 * @param estimated
	 * @param freq
	 * @return
	 */
	public static RegP get(String tag, Map<Integer, File> int2file, Calendar begin, Calendar end, boolean estimated,
			int freq) {
		final RegP regs = new RegP(estimated ? 2 : 1, freq);
		get(new Flow<Med>() {
			@Override
			public void incomingData(Med data) {
				regs.put(data, data.isEstimated() ? 1 : 0);
			}
		}, int2file, tag, begin, end, estimated, freq);
		return regs;
	}

	// discreto

	/**
	 * UMA TAG, DISCRETO, FLOW
	 * 
	 * @param flow
	 * @param int2file
	 * @param tag
	 * @param cs
	 * @param estimated
	 */
	public static void get(Flow<Med> flow, Map<Integer, File> int2file, String tag, Calendar[] cs, boolean estimated) {
		Arrays.sort(cs);

		// vetor ordenado de dias em que se quer pegar medições e truncar horas
		// e segundos
		TreeSet<Calendar> days = new TreeSet<>();
		for (int i = 0; i < cs.length; i++)
			days.add(new GregorianCalendar(cs[i].get(Calendar.YEAR), cs[i].get(Calendar.MONTH),
					cs[i].get(Calendar.DAY_OF_MONTH)));

		// em cada dia procurar os horários solicitados
		int previous = 0;
		for (Calendar r : days) {
			// começo do dia
			Calendar inf = r;

			// final do dia
			Calendar sup = (Calendar) r.clone();
			sup.set(Calendar.HOUR_OF_DAY, 23);
			sup.set(Calendar.MINUTE, 59);

			// ver quem é que do vetor de entrada está neste dia
			int[] ds = ArrayUtils.getRange(previous, cs, inf, sup);
			Calendar[] cds = Arrays.copyOfRange(cs, ds[0], ds[1]);

			// das entradas procuradas que estão nesse dia, ver quantos minutos
			// são desde a meia-noite
			int[] minutes = new int[cds.length];
			for (int i = 0; i < minutes.length; i++)
				minutes[i] = cds[i].get(Calendar.HOUR_OF_DAY) * 60 + cds[i].get(Calendar.MINUTE);

			// ---------------- VARRER O DIA ----------------

			File f = int2file.get(TimeUtils.date2int(r));
			if (f == null)
				// se o arquivo não for encontrado, próxima data
				continue;

			InputStream is = null;
			try {
				is = new FileInputStream(f);
			} catch (FileNotFoundException | NullPointerException e1) {
				// se o arquivo não for encontrado, próxima data
				continue;
			}
			DataInputStream dis = new DataInputStream(is);

			try {
				// número de registros
				byte[] b = new byte[4];
				dis.read(b);
				int registros = readInt(b);

				// ultima atualização
				dis.skip(POS_ATUALIZACAO);
				int bytes = dis.readByte();

				// procura o número do registro procurado
				int numRegHist = -1;

				b = new byte[POS_TAG_INFO];
				for (int l = 0; l < registros; l++) {
					if (numRegHist < 0) {
						// se ainda não achou, continua
						dis.read(b);
						String t = new String(b, POS_TAG, TAG_SIZE).trim();

						if (t.equalsIgnoreCase(tag.trim()))
							numRegHist = l;
					} else // se já achou
						dis.skip(POS_TAG_INFO);
				}

				// se o registro não for encontrado
				if (numRegHist < 0) {
					try {
						dis.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					// próxima data
					continue;
				}
				b = new byte[bytes];

				// cabeçalho indicando a data e hora
				byte[] bh = new byte[POS_DATA_HEADER];
				// cada registro tem 9 bytes a cada minuto
				int dadosDoMinuto = bytes * registros;
				// tamanho do bloco do minuto
				int blocoLength = POS_DATA_HEADER + dadosDoMinuto;
				// número de blocos para cada instante de tempo
				int blocos = dis.available() / blocoLength;
				// espaço de tempo entre blocos
				int intervalo = MIN_DAY / blocos;

				// índice do minuto procurado (começa-se pelo primeiro)
				int n = 0;
				// para todos os minutos do dia
				for (int l = 0; l < blocos; l++) {
					// ver se este minuto é o procurado
					if (l * intervalo == minutes[n]) {
						// data
						dis.read(bh);
						String data = new String(bh, 0, POS_DATA_HEADER_SEM_MS);

						// leitura do instante de tempo das medições
						Calendar c = null;
						try {
							c = TimeUtils.date2Calendar(getDate(data));
						} catch (ParseException e) {
							System.err.println("Formato de data não reconhecido (2): " + data + " no arquivo " + f);
						}

						if (c != null) {
							for (int k = 0; k < registros; k++) {
								if (k != numRegHist) {
									// pula os registros que não nos interessam
									dis.skip(bytes);
								} else {
									// lê-se o que nos interessa
									dis.read(b);

									// **** GRANDEZA ****
									byte[] b1 = Arrays.copyOfRange(b, 0, 4);

									float v = ByteBuffer.wrap(b1).order(ORDER).getFloat();

									flow.incomingData(new Med(c, v));

									// se forem pedidos os valores estimados
									if (estimated) {
										byte mascara = b[8];
										// se na máscara disserem que há o
										// valor estimado
										if ((TEM_ESTIM & mascara) != 0) {
											// **** GRANDEZA ESTIMADA ****

											b1 = Arrays.copyOfRange(b, 4, 8);

											v = ByteBuffer.wrap(b1).order(ORDER).getFloat();

											Med m = new Med(c, v);
											m.setEstimated();
											flow.incomingData(m);
										}
									}
								}
							}
						}
						// pula para o próximo instante procurado
						n++;
						// se já leu todos os minutos do dia, pula para o
						// próximo dia
						if (n == minutes.length)
							break;
					} else {
						dis.skip(blocoLength);
					}
				}

				if (dis != null)
					dis.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	/**
	 * UMA TAG, DISCRETO, REG
	 * 
	 * @param tag
	 * @param int2file
	 * @param cs
	 * @param estimated
	 * @return
	 */
	public static RegP get(String tag, Map<Integer, File> int2file, Calendar[] cs, boolean estimated) {
		final RegP reg = new RegP(estimated ? 2 : 1);
		get(new Flow<Med>() {
			@Override
			public void incomingData(Med data) {
				reg.put(data, data.isEstimated() ? 1 : 0);
			}
		}, int2file, tag, cs, estimated);
		return reg;
	}

	/**
	 * UMA TAG, UM INSTANTE DE TEMPO, MEDS, BUSCA RÁPIDA
	 * 
	 * @param f
	 * @param date
	 * @param seconds distância, em segundos, entre duas medições
	 * @param angle
	 * @return
	 */
	public static Meds get(File f, Calendar date, int seconds, int... posTags) {
		InputStream is = null;
		try {
			is = new FileInputStream(f);
		} catch (FileNotFoundException | NullPointerException e1) {
			return null;
		}
		DataInputStream dis = new DataInputStream(is);

		Meds out = null;
		try {
			// número de registros
			byte[] b = new byte[4];
			dis.read(b);
			int registros = readInt(b);

			// ultima atualização
			dis.skip(POS_ATUALIZACAO);

			// número de bytes por medição
			int bytes = dis.readByte();

			// procura o número do registro procurado
			for (int l = 0; l < registros; l++) // vai pro final
				dis.skip(POS_TAG_INFO);

			// cada registro tem 9 bytes a cada minuto
			int dadosDoMinuto = bytes * registros;
			// tamanho do bloco do minuto
			int blocoLength = POS_DATA_HEADER + dadosDoMinuto;

			int posBlock = ((date.get(Calendar.HOUR_OF_DAY) * 3600 + date.get(Calendar.MINUTE) * 60
					+ date.get(Calendar.SECOND)) / seconds) - 60;

			// para todos os minutos do dia

			// pula até o minuto desejado
			for (int l = 0; l < posBlock; l++)
				dis.skip(blocoLength);

			// cabeçalho indicando a data e hora
			b = new byte[POS_DATA_HEADER];
			dis.read(b);
			Calendar c = null;
			{
				String data = new String(b, 0, POS_DATA_HEADER_SEM_MS);
				try {
					c = TimeUtils.date2Calendar(getDate(data));
				} catch (ParseException e) {
					System.err.println("Formato de data não reconhecido (3): " + data + " no arquivo " + f);
				}
			}

			b = new byte[dadosDoMinuto];
			dis.read(b);

			float[] values = new float[posTags.length];
			for (int p = 0; p < posTags.length; p++) {
				int pos = posTags[p] * bytes;
				byte[] bf = Arrays.copyOfRange(b, pos, pos + 4);
				values[p] = ByteBuffer.wrap(bf).order(ORDER).getFloat();
			}
			if (c != null)
				out = new Meds(c, values);

			if (dis != null)
				dis.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return out;
	}

	/**
	 * Função que acha a posição de uma tag num arquivo HIS. Função auxiliar de
	 * {@link #get(File, Calendar, int, int...)}
	 * 
	 * @param f
	 * @param tag
	 * @return
	 */
	public static int getPos(File f, String tag) {
		InputStream is = null;
		try {
			is = new FileInputStream(f);
		} catch (FileNotFoundException | NullPointerException e1) {
			return -1;
		}
		DataInputStream dis = new DataInputStream(is);

		try {
			// número de registros
			byte[] b = new byte[4];
			dis.read(b);
			int registros = readInt(b);

			// ultima atualização e número de bytes por medição
			dis.skip(POS_ATUALIZACAO + 1);

			b = new byte[POS_TAG_INFO];
			// procura o número do registro procurado
			for (int l = 0; l < registros; l++) {
				// se ainda achou, continua
				dis.read(b);
				String t = new String(b, POS_TAG, TAG_SIZE).trim();

				if (t.equalsIgnoreCase(tag)) {
					dis.close();
					return l;
				}
			}
			dis.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return -1;
	}

	// =========================== PEGAR VÁRIAS TAGS ===========================

	// contínuo

	/**
	 * MAIS DE UMA TAG, CONTÍNUO, FLOW
	 * 
	 * @param flow
	 * @param int2file
	 * @param begin
	 * @param end
	 * @param estimated
	 * @param freq
	 * @param tags
	 */
	public static void get(Flow<Med> flow, Map<Integer, File> int2file, Calendar begin, Calendar end, boolean estimated,
			int freq, String... tags) {
		Calendar r = (Calendar) begin.clone();

		// para todos os dias do período
		while (r.before(end)) {
			File f = int2file.get(TimeUtils.date2int(r));

			InputStream is = null;
			try {
				is = new FileInputStream(f);
			} catch (FileNotFoundException | NullPointerException e1) {
				// se o arquivo não for encontrado, pula um dia
				r.add(Calendar.DAY_OF_MONTH, 1);
				r.set(Calendar.HOUR_OF_DAY, 0);
				r.set(Calendar.MINUTE, 0);
				continue;
			}
			DataInputStream dis = new DataInputStream(is);

			try {
				// número de registros
				byte[] b = new byte[4];
				dis.read(b);
				int registros = readInt(b);

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
					// se o registro procurado não estiver no cabeçalho do
					// arquivo, pula para o próximo dia
					try {
						dis.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					// pula um dia
					r.add(Calendar.DAY_OF_MONTH, 1);
					r.set(Calendar.HOUR_OF_DAY, 0);
					r.set(Calendar.MINUTE, 0);
					continue;
				}
				b = new byte[bytes];

				// cabeçalho indicando a data e hora
				byte[] bh = new byte[POS_DATA_HEADER];
				// cada registro tem 9 bytes a cada minuto
				int dadosDoMinuto = bytes * registros;
				// tamanho do bloco do minuto
				int blocoLength = POS_DATA_HEADER + dadosDoMinuto;
				// número de blocos para cada instante de tempo
				int blocos = dis.available() / blocoLength;
				// número de blocos a serem pulados
				int amostra = 1;
				if (freq > 0) {
					// espaço de tempo entre blocos
					int intervalo = MIN_DAY / blocos;
					amostra = (int) Math.ceil(((double) freq) / intervalo);
				}

				// para todos os minutos do dia
				for (int l = 0; l < blocos; l++) {
					// taxa de amostragem
					if (l % amostra == 0) {
						// data
						dis.read(bh);
						String data = new String(bh, 0, POS_DATA_HEADER_SEM_MS);

						// leitura do instante de tempo das medições
						Calendar c = null;
						try {
							c = TimeUtils.date2Calendar(getDate(data));
						} catch (ParseException e1) {
							System.err.println("Formato de data não reconhecido (4): " + data + " no arquivo " + f);
						}

						if (c != null ? !c.before(begin) && c.before(end) : false) {
							// se estiver dentro do período de tempo procurado,
							// lê-se, senão pula begin <= c < end

							// dados
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

									// **** GRANDEZA ****
									byte[] b1 = Arrays.copyOfRange(b, 0, 4);

									float v = ByteBuffer.wrap(b1).order(ORDER).getFloat();

									Med m = new Med(c, v);
									m.setChannel(e.getValue());
									flow.incomingData(m);

									// se forem pedidos os valores estimados
									if (estimated) {
										byte mascara = b[8];
										// se na máscara disserem que há o
										// valor estimado
										if ((TEM_ESTIM & mascara) != 0) {
											// **** GRANDEZA ESTIMADA ****

											b1 = Arrays.copyOfRange(b, 4, 8);

											v = ByteBuffer.wrap(b1).order(ORDER).getFloat();

											m = new Med(c, v);
											m.setEstimated();
											m.setChannel(tags.length + e.getValue());
											flow.incomingData(m);
										}
									}

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
					} else
						dis.skip(blocoLength);
				}

				if (dis != null)
					dis.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			// próximo dia
			r.add(Calendar.DAY_OF_MONTH, 1);
			r.set(Calendar.HOUR_OF_DAY, 0);
			r.set(Calendar.MINUTE, 0);
		}
	}

	/**
	 * MAIS DE UMA TAG, CONTÍNUO, REG
	 * 
	 * @param begin
	 * @param end
	 * @param int2file
	 * @param estimated
	 * @param freq
	 * @param tags
	 * @return
	 */
	public static RegP get(Calendar begin, Calendar end, Map<Integer, File> int2file, boolean estimated, int freq,
			String... tags) {
		final RegP regs = new RegP(tags.length + (estimated ? tags.length : 0), freq);
		get(new Flow<Med>() {
			@Override
			public void incomingData(Med data) {
				regs.put(data, data.getChannel());
			}
		}, int2file, begin, end, estimated, freq, tags);
		return regs;
	}

	/**
	 * MAIS DE UMA TAG, CONTÍNUO, REG SEM FREQUENCIA FIXA
	 * 
	 * @param begin
	 * @param end
	 * @param int2file
	 * @param estimated
	 * @param tags
	 * @return
	 */
	public static Reg get(Calendar begin, Calendar end, Map<Integer, File> int2file, boolean estimated,
			String... tags) {
		final Reg regs = new Reg(tags.length + (estimated ? tags.length : 0));
		get(new Flow<Med>() {
			@Override
			public void incomingData(Med data) {
				regs.put(data, data.getChannel());
			}
		}, int2file, begin, end, estimated, -1, tags);
		return regs;
	}

	/**
	 * MAIS DE UMA TAG, CONTÍNUO, FLOW DIRECIONADO
	 * 
	 * @param flow
	 * @param int2file
	 * @param begin
	 * @param end
	 * @param freq
	 * @param tag2chs
	 */
	public static void get(Flow<Med> flow, Map<Integer, File> int2file, Calendar begin, Calendar end, int freq,
			Map<String, Set<Integer>> tag2chs) {
		Calendar r = (Calendar) begin.clone();

		// para todos os dias do período
		while (r.before(end)) {
			File f = int2file.get(TimeUtils.date2int(r));

			InputStream is = null;
			try {
				is = new FileInputStream(f);
			} catch (FileNotFoundException | NullPointerException e1) {
				// se o arquivo não for encontrado, pula um dia
				r.add(Calendar.DAY_OF_MONTH, 1);
				r.set(Calendar.HOUR_OF_DAY, 0);
				r.set(Calendar.MINUTE, 0);
				continue;
			}
			DataInputStream dis = new DataInputStream(is);

			try {
				// número de registros
				byte[] b = new byte[4];
				dis.read(b);
				int registros = readInt(b);

				// ultima atualização
				dis.skip(POS_ATUALIZACAO);
				int bytes = dis.readByte();

				// tabela de dispersão que associa para cada posição da tag no
				// arquivo a posição dele no vetor de entrada desta função
				TreeMap<Integer, Set<Integer>> posFile2ch = new TreeMap<>();

				b = new byte[POS_TAG_INFO];
				// procura o número do registro procurado
				for (int l = 0; l < registros; l++) {
					if (posFile2ch.size() < tag2chs.size()) {
						// se ainda não achou todo mundo, continua
						dis.read(b);
						String t = new String(b, POS_TAG, TAG_SIZE).trim();

						Set<Integer> chs = tag2chs.get(t);
						if (chs != null) // se for uma das tags procuradas
							posFile2ch.put(l, chs);
					} else // se já achou todo mundo, vai pro final
						dis.skip(POS_TAG_INFO);
				}

				if (posFile2ch.size() == 0) {
					// se o registro procurado não estiver no cabeçalho do
					// arquivo, pula para o próximo dia
					try {
						dis.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					// pula um dia
					r.add(Calendar.DAY_OF_MONTH, 1);
					r.set(Calendar.HOUR_OF_DAY, 0);
					r.set(Calendar.MINUTE, 0);
					continue;
				}
				b = new byte[bytes];

				// cabeçalho indicando a data e hora
				byte[] bh = new byte[POS_DATA_HEADER];
				// cada registro tem 9 bytes a cada minuto
				int dadosDoMinuto = bytes * registros;
				// tamanho do bloco do minuto
				int blocoLength = POS_DATA_HEADER + dadosDoMinuto;
				// número de blocos para cada instante de tempo
				int blocos = dis.available() / blocoLength;
				// número de blocos a serem pulados
				int amostra = 1;
				if (freq > 0) {
					// espaço de tempo entre blocos
					int intervalo = MIN_DAY / blocos;
					amostra = (int) Math.ceil(((double) freq) / intervalo);
				}

				// para todos os minutos do dia
				for (int l = 0; l < blocos; l++) {
					// taxa de amostragem
					if (l % amostra == 0) {
						// data
						dis.read(bh);
						String data = new String(bh, 0, POS_DATA_HEADER_SEM_MS);

						// leitura do instante de tempo das medições
						Calendar c = null;
						try {
							c = TimeUtils.date2Calendar(getDate(data));
						} catch (ParseException e1) {
							System.err.println("Formato de data não reconhecido (5): " + data + " no arquivo " + f);
						}

						// se estiver dentro do período de tempo procurado, lê-se, senão pula
						// begin <= c < end
						if (c != null ? !c.before(begin) && c.before(end) : false) {
							// dados
							Iterator<Entry<Integer, Set<Integer>>> ite = posFile2ch.entrySet().iterator();
							Entry<Integer, Set<Integer>> e = ite.next();
							int posFile = e.getKey();
							for (int k = 0; k < registros; k++) {
								if (k != posFile) {
									// pula os registros que não nos interessam
									dis.skip(bytes);
								} else {
									// lê-se o que nos interessa
									dis.read(b);

									// **** GRANDEZA ****
									byte[] b1 = Arrays.copyOfRange(b, 0, 4);

									float v = ByteBuffer.wrap(b1).order(ORDER).getFloat();

									Set<Integer> chs = e.getValue();
									Med m = new Med(c, v);
									for (Integer ch : chs) {
										m.setChannel(ch);
										flow.incomingData(m);
									}

									// procura-se o próximo
									if (ite.hasNext()) {
										e = ite.next();
										posFile = e.getKey();
									} else
										e = null;
								}
							}
						} else {
							dis.skip(dadosDoMinuto);
						}
					} else {
						dis.skip(blocoLength);
					}
				}

				if (dis != null)
					dis.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			// próximo dia
			r.add(Calendar.DAY_OF_MONTH, 1);
			r.set(Calendar.HOUR_OF_DAY, 0);
			r.set(Calendar.MINUTE, 0);
		}
	}

	// discreto

	/**
	 * MAIS DE UMA TAG, DISCRETO, FLOW
	 * 
	 * @param flow
	 * @param int2file
	 * @param cs
	 * @param estimated
	 * @param tags
	 */
	public static void get(Flow<Med> flow, Map<Integer, File> int2file, Calendar[] cs, boolean estimated,
			String... tags) {
		Arrays.sort(cs);

		// vetor ordenado de dias em que se quer pegar medições
		TreeSet<Calendar> days = new TreeSet<>();
		for (int i = 0; i < cs.length; i++)
			days.add(new GregorianCalendar(cs[i].get(Calendar.YEAR), cs[i].get(Calendar.MONTH),
					cs[i].get(Calendar.DAY_OF_MONTH)));

		// em cada dia procurar os horários solicitados
		int previous = 0;
		for (Calendar r : days) {
			// começo do dia
			Calendar inf = r;

			// final do dia
			Calendar sup = (Calendar) r.clone();
			sup.set(Calendar.HOUR_OF_DAY, 23);
			sup.set(Calendar.MINUTE, 59);

			// ver quem é que do vetor de entrada está neste dia
			int[] ds = ArrayUtils.getRange(previous, cs, inf, sup);
			Calendar[] cds = Arrays.copyOfRange(cs, ds[0], ds[1]);
			previous = ds[1];

			// das entradas procuradas que estão nesse dia, ver quantos minutos
			// são desde a meia-noite
			int[] minutes = new int[cds.length];
			for (int i = 0; i < minutes.length; i++)
				minutes[i] = cds[i].get(Calendar.HOUR_OF_DAY) * 60 + cds[i].get(Calendar.MINUTE);

			// ---------------- VARRER O DIA ----------------
			File f = int2file.get(TimeUtils.date2int(r));
			if (f == null)
				// se o arquivo não for encontrado, próxima data
				continue;

			InputStream is = null;
			try {
				is = new FileInputStream(f);
			} catch (FileNotFoundException | NullPointerException e1) {
				// se o arquivo não for encontrado, próxima data
				continue;
			}
			DataInputStream dis = new DataInputStream(is);

			try {
				// número de registros
				byte[] b = new byte[4];
				dis.read(b);
				int registros = readInt(b);

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
					// se o registro procurado não estiver no cabeçalho do
					// arquivo, pula para o próximo dia
					try {
						dis.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					// próxima data
					continue;
				}
				b = new byte[bytes];

				// cabeçalho indicando a data e hora
				byte[] bh = new byte[POS_DATA_HEADER];
				// cada registro tem 9 bytes a cada minuto
				int dadosDoMinuto = bytes * registros;
				// tamanho do bloco do minuto
				int blocoLength = POS_DATA_HEADER + dadosDoMinuto;
				// número de blocos para cada instante de tempo
				int blocos = dis.available() / blocoLength;
				// espaço de tempo entre blocos
				int intervalo = MIN_DAY / blocos;

				// índice do minuto procurado (começa-se pelo primeiro)
				int n = 0;
				// para todos os minutos do dia
				for (int l = 0; l < blocos; l++) {
					// ver se este minuto é o procurado
					if (l * intervalo == minutes[n]) {
						// data
						dis.read(bh);
						String data = new String(bh, 0, POS_DATA_HEADER_SEM_MS);

						// leitura do instante de tempo das medições ()

						Calendar c = null;
						try {
							c = TimeUtils.date2Calendar(getDate(data));
						} catch (ParseException e1) {
							System.err.println("Formato de data não reconhecido (6): " + data + " no arquivo " + f);
						}

						if (c != null) { // dados
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

									// **** GRANDEZA ****
									byte[] b1 = Arrays.copyOfRange(b, 0, 4);

									float v = ByteBuffer.wrap(b1).order(ORDER).getFloat();

									Med m = new Med(c, v);
									m.setChannel(e.getValue());
									flow.incomingData(m);

									m.setMask(b[8]);// nono byte

									// se forem pedidos os valores estimados
									if (estimated) {
										// se na máscara disserem que há o valor estimado
										if ((TEM_ESTIM & b[8]) != 0) {
											// **** GRANDEZA ESTIMADA ****

											b1 = Arrays.copyOfRange(b, 4, 8);

											v = ByteBuffer.wrap(b1).order(ORDER).getFloat();

											m = new Med(c, v);
											m.setEstimated();
											m.setChannel(tags.length + e.getValue());
											flow.incomingData(m);
										}
									}

									// procura-se o próximo
									if (ite.hasNext()) {
										e = ite.next();
										posFile = e.getKey();
									} else
										e = null;
								}
							}
						}
						// pula para o próximo instante procurado
						n++;
						// se já leu todos os minutos do dia, pula para o
						// próximo dia
						if (n == minutes.length)
							break;
					} else
						dis.skip(blocoLength);
				}

				if (dis != null)
					dis.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * MAIS DE UMA TAG, DISCRETO, REG
	 * 
	 * @param cs
	 * @param status
	 * @param estimated
	 * @param tags
	 * @return
	 */
	public static RegP get(Calendar[] cs, Map<Integer, File> int2file, boolean estimated, String... tags) {
		final RegP regs = new RegP(tags.length + (estimated ? tags.length : 0));
		get(new Flow<Med>() {
			@Override
			public void incomingData(Med data) {
				regs.put(data, data.getChannel());
			}
		}, int2file, cs, estimated, tags);
		return regs;
	}

	/**
	 * MAIS DE UMA TAG, DISCRETO, FLOW DIRECIONADO
	 * 
	 * @param flow
	 * @param int2file
	 * @param cs
	 * @param tag2chs
	 */
	public static void get(Flow<Med> flow, Map<Integer, File> int2file, Calendar[] cs,
			Map<String, Set<Integer>> tag2chs) {
		Arrays.sort(cs);

		// vetor ordenado de dias em que se quer pegar medições
		TreeSet<Calendar> days = new TreeSet<>();
		for (int i = 0; i < cs.length; i++)
			days.add(new GregorianCalendar(cs[i].get(Calendar.YEAR), cs[i].get(Calendar.MONTH),
					cs[i].get(Calendar.DAY_OF_MONTH)));

		// em cada dia procurar os horários solicitados
		int previous = 0;
		for (Calendar r : days) {
			// começo do dia
			Calendar inf = r;

			// final do dia
			Calendar sup = (Calendar) r.clone();
			sup.set(Calendar.HOUR_OF_DAY, 23);
			sup.set(Calendar.MINUTE, 59);

			// ver quem é que do vetor de entrada está neste dia
			int[] ds = ArrayUtils.getRange(previous, cs, inf, sup);
			Calendar[] cds = Arrays.copyOfRange(cs, ds[0], ds[1]);
			previous = ds[1];

			// das entradas procuradas que estão nesse dia, ver quantos minutos
			// são desde a meia-noite
			int[] minutes = new int[cds.length];
			for (int i = 0; i < minutes.length; i++)
				minutes[i] = cds[i].get(Calendar.HOUR_OF_DAY) * 60 + cds[i].get(Calendar.MINUTE);

			// ---------------- VARRER O DIA ----------------
			File f = int2file.get(TimeUtils.date2int(r));
			if (f == null)
				// se o arquivo não for encontrado, próxima data
				continue;

			InputStream is = null;
			try {
				is = new FileInputStream(f);
			} catch (FileNotFoundException | NullPointerException e1) {
				// se o arquivo não for encontrado, próxima data
				continue;
			}
			DataInputStream dis = new DataInputStream(is);

			try {
				// número de registros
				byte[] b = new byte[4];
				dis.read(b);
				int registros = readInt(b);

				// ultima atualização
				dis.skip(POS_ATUALIZACAO);
				int bytes = dis.readByte();

				// tabela de dispersão que associa para cada posição da tag no
				// arquivo a posição dele no vetor de entrada desta função
				TreeMap<Integer, Set<Integer>> posFile2chs = new TreeMap<>();

				b = new byte[POS_TAG_INFO];
				// procura o número do registro procurado
				for (int l = 0; l < registros; l++) {
					if (posFile2chs.size() < tag2chs.size()) {
						// se ainda não achou todo mundo, continua
						dis.read(b);
						String t = new String(b, POS_TAG, TAG_SIZE).trim();

						Set<Integer> chs = tag2chs.get(t);
						if (chs != null) // se for uma das tags procuradas
							posFile2chs.put(l, chs);
					} else // se já achou todo mundo, vai pro final
						dis.skip(POS_TAG_INFO);
				}

				if (posFile2chs.size() == 0) {
					// se o registro procurado não estiver no cabeçalho do
					// arquivo, pula para o próximo dia
					try {
						dis.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					// próxima data
					continue;
				}
				b = new byte[bytes];

				// cabeçalho indicando a data e hora
				byte[] bh = new byte[POS_DATA_HEADER];
				// cada registro tem 9 bytes a cada minuto
				int dadosDoMinuto = bytes * registros;
				// tamanho do bloco do minuto
				int blocoLength = POS_DATA_HEADER + dadosDoMinuto;
				// número de blocos para cada instante de tempo
				int blocos = dis.available() / blocoLength;
				// espaço de tempo entre blocos
				int intervalo = MIN_DAY / blocos;

				// índice do minuto procurado (começa-se pelo primeiro)
				int n = 0;
				// para todos os minutos do dia
				for (int l = 0; l < blocos; l++) {
					// ver se este minuto é o procurado
					if (l * intervalo == minutes[n]) {
						// data
						dis.read(bh);
						String data = new String(bh, 0, POS_DATA_HEADER_SEM_MS);

						// leitura do instante de tempo das medições
						Calendar c = null;
						try {
							c = TimeUtils.date2Calendar(getDate(data));
						} catch (ParseException e1) {
							System.err.println("Formato de data não reconhecido (7): " + data + " no arquivo " + f);
						}

						if (c != null ? c.get(Calendar.DAY_OF_MONTH) == r.get(Calendar.DAY_OF_MONTH) : false) {
							// deve-se verificar se o dia é o mesmo pois no caso dos arquivos com medições
							// em tempo real do RegHist e nos arquivos gerados pelo Importador, há, num
							// mesmo arquivo, medições de dias diferentes

							// dados
							Iterator<Entry<Integer, Set<Integer>>> ite = posFile2chs.entrySet().iterator();
							Entry<Integer, Set<Integer>> e = ite.next();
							int posFile = e.getKey();
							for (int k = 0; k < registros; k++) {
								if (k != posFile) {
									// pula os registros que não nos interessam
									dis.skip(bytes);
								} else {
									// lê-se o que nos interessa
									dis.read(b);

									// **** GRANDEZA ****
									byte[] b1 = Arrays.copyOfRange(b, 0, 4);

									float v = ByteBuffer.wrap(b1).order(ORDER).getFloat();

									Med m = new Med(c, v);
									Set<Integer> chs = e.getValue();
									for (Integer ch : chs) {
										m.setChannel(ch);
										flow.incomingData(m);
									}

									// procura-se o próximo
									if (ite.hasNext()) {
										e = ite.next();
										posFile = e.getKey();
									} else
										e = null;
								}
							}
						}
						// pula para o próximo instante procurado
						n++;
						// se já leu todos os minutos do dia, pula para o
						// próximo dia
						if (n == minutes.length)
							break;
					} else
						dis.skip(blocoLength);
				}

				if (dis != null)
					dis.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	// última medição

	/**
	 * MAIS DE UMA TAG, ÚLTIMA MEDIÇÃO
	 * 
	 * @param f
	 * @param readAll <code>true</code> para que o arquivo seja lido inteiramente,  
	 *                             <code>false</code> para que ele seja lido só até
	 *                o primeiro                degrau (isto é, a primeira data que
	 *                seja anterior à última)
	 * @param tags
	 * @return
	 */
	public static Meds get(File f, boolean readAll, String... tags) {
		InputStream is = null;
		try {
			is = new FileInputStream(f);
		} catch (FileNotFoundException | NullPointerException e1) {
			return null;
		}
		DataInputStream dis = new DataInputStream(is);

		Meds out = null;
		try {
			// número de registros
			byte[] b = new byte[4];
			dis.read(b);
			int registros = readInt(b);

			// ultima atualização
			dis.skip(POS_ATUALIZACAO);

			// número de bytes por medição
			int bytes = dis.readByte();

			// tabela de dispersão que associa para cada posição da tag no
			// arquivo a posição dele no vetor de entrada desta função
			HashMap<Integer, Integer> posFile2ch = new HashMap<>();

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
				return null;
			}

			// cabeçalho indicando a data e hora
			b = new byte[POS_DATA_HEADER];
			// cada registro tem 9 bytes a cada minuto
			int dadosDoMinuto = bytes * registros;
			// tamanho do bloco do minuto
			int blocoLength = POS_DATA_HEADER + dadosDoMinuto;
			// número de blocos para cada instante de tempo
			int blocos = dis.available() / blocoLength;

			Calendar c = null;

			// para todos os minutos do dia
			byte[] last = new byte[dadosDoMinuto];

			if (readAll) {
				for (int l = 0; l < blocos; l++) {
					// leitura do instante de tempo das medições
					dis.read(b);
					Calendar c0 = null;
					String data = new String(b, 0, POS_DATA_HEADER_SEM_MS);
					try {
						c0 = TimeUtils.date2Calendar(getDate(data));
					} catch (ParseException e1) {
						System.err.println("Formato de data não reconhecido (8): " + data + " no arquivo " + f);
					}

					dis.read(last);
					if (c != null ? (c0 != null ? c0.after(c) : false) : true) {
						// se a medição de depois é de uma data anterior à última, exporta a última
						c = c0;
						float[] values = new float[tags.length];
						for (Entry<Integer, Integer> e : posFile2ch.entrySet()) {
							int pos = e.getKey() * bytes;
							byte[] bf = Arrays.copyOfRange(last, pos, pos + 4);
							values[e.getValue()] = ByteBuffer.wrap(bf).order(ORDER).getFloat();
						}
						out = new Meds(c, values);
					}
				}
			} else {
				for (int l = 0; l <= blocos; l++) {
					// lê-se o arquivo até um número l = blocos pois quando se chega no final do
					// arquivo, as funções 'read' não alteram o vetor de bytes, de modo que c == c0,
					// lendo-se a última medição

					// leitura do instante de tempo das medições
					dis.read(b);
					Calendar c0 = null;
					{
						String data = new String(b, 0, POS_DATA_HEADER_SEM_MS);
						try {
							c0 = TimeUtils.date2Calendar(getDate(data));
						} catch (ParseException e1) {
							System.err.println("Formato de data não reconhecido (9): " + data + " no arquivo " + f);
						}
					}

					if (c != null ? (c0 != null ? !c.before(c0) : false) : false) {
						// se a medição de depois é de uma data anterior à última, exporta a última
						float[] values = new float[tags.length];
						for (Entry<Integer, Integer> e : posFile2ch.entrySet()) {
							int pos = e.getKey() * bytes;
							if (bytes == 9 ? (last[pos + 9] & FALHA_COM) > 0 : false) // se vier com falha de
																						// comunicação
								continue;
							byte[] bf = Arrays.copyOfRange(last, pos, pos + 4);
							values[e.getValue()] = ByteBuffer.wrap(bf).order(ORDER).getFloat();
						}
						out = new Meds(c, values);
						break;
					} else
						c = c0;
					dis.read(last);
				}
			}

			if (dis != null)
				dis.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return out;
	}

	/**
	 * DIGITAIS, MAIS DE UMA TAG, CONTÍNUO
	 * 
	 * @param begin
	 * @param end
	 * @param int2file
	 * @param tags
	 * @return
	 */
	public static Seq<Boolean> get(Calendar begin, Calendar end, Map<Integer, File> int2file, String... tags) {
		Seq<Boolean> out = new Seq<>(tags);

		Calendar r = (Calendar) begin.clone();
		boolean[] status = new boolean[tags.length];

		// para todos os dias do período
		while (r.before(end)) {
			File f = int2file.get(TimeUtils.date2int(r));

			InputStream is = null;
			try {
				is = new FileInputStream(f);
			} catch (FileNotFoundException | NullPointerException e1) {
				// se o arquivo não for encontrado, pula um dia
				r.add(Calendar.DAY_OF_MONTH, 1);
				r.set(Calendar.HOUR_OF_DAY, 0);
				r.set(Calendar.MINUTE, 0);
				continue;
			}
			DataInputStream dis = new DataInputStream(is);

			try {
				// número de registros
				byte[] b = new byte[4];
				dis.read(b);
				int registros = readInt(b);

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
					// se o registro procurado não estiver no cabeçalho do arquivo, pula para o
					// próximo dia
					try {
						dis.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					// pula um dia
					r.add(Calendar.DAY_OF_MONTH, 1);
					r.set(Calendar.HOUR_OF_DAY, 0);
					r.set(Calendar.MINUTE, 0);
					continue;
				}
				b = new byte[bytes];

				// cabeçalho indicando a data e hora
				byte[] bh = new byte[POS_DATA_HEADER];
				// cada registro tem 9 bytes a cada minuto
				int dadosDoMinuto = bytes * registros;
				// tamanho do bloco do minuto
				int blocoLength = POS_DATA_HEADER + dadosDoMinuto;
				// número de blocos para cada instante de tempo
				int blocos = dis.available() / blocoLength;

				// para todos os minutos do dia
				for (int l = 0; l < blocos; l++) {
					// data
					dis.read(bh);
					String data = new String(bh, 0, POS_DATA_HEADER_SEM_MS);

					// leitura do instante de tempo das medições
					Calendar c = null;
					try {
						c = TimeUtils.date2Calendar(getDate(data));
					} catch (ParseException e1) {
						System.err.println("Formato de data não reconhecido (10): " + data + " no arquivo " + f);
					}

					if (c != null ? !c.before(begin) && c.before(end) : false) {
						// se estiver dentro do período de tempo procurado, lê-se, senão pula
						// begin <= c < end

						// dados
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

								boolean v = b[0] == 1;
								int pos = e.getValue();

								if (v ^ status[pos]) {
									status[pos] = v;
									out.put(c, pos, v);
								}

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

			// próximo dia
			r.add(Calendar.DAY_OF_MONTH, 1);
			r.set(Calendar.HOUR_OF_DAY, 0);
			r.set(Calendar.MINUTE, 0);
		}
		return out;
	}

	/**
	 * DIGITAIS, MAIS DE UMA TAG, ÚLTIMA MEDIÇÃO
	 * 
	 * @param readAll <code>true</code> para que o arquivo seja lido inteiramente,  
	 *                             <code>false</code> para que ele seja lido só até
	 *                o primeiro                degrau (isto é, a primeira data que
	 *                seja anterior à última)
	 * @param f
	 * @param tags
	 * @return
	 */
	public static boolean[] get(boolean readAll, File f, String... tags) {
		InputStream is = null;
		try {
			is = new FileInputStream(f);
		} catch (FileNotFoundException | NullPointerException e1) {
			return null;
		}
		DataInputStream dis = new DataInputStream(is);

		boolean[] out = null;
		try {
			// número de registros
			byte[] b = new byte[4];
			dis.read(b);
			int registros = readInt(b);

			// ultima atualização
			dis.skip(POS_ATUALIZACAO);

			// número de bytes por medição
			int bytes = dis.readByte();

			// tabela de dispersão que associa para cada posição da tag no arquivo a posição
			// dele no vetor de entrada desta função
			HashMap<Integer, Integer> posFile2ch = new HashMap<>();

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

			// cabeçalho indicando a data e hora
			b = new byte[POS_DATA_HEADER];
			// cada registro tem 9 bytes a cada minuto
			int dadosDoMinuto = bytes * registros;
			// tamanho do bloco do minuto
			int blocoLength = POS_DATA_HEADER + dadosDoMinuto;
			// número de blocos para cada instante de tempo
			int blocos = dis.available() / blocoLength;

			Calendar c = null;

			// para todos os minutos do dia
			byte[] last = new byte[dadosDoMinuto];

			if (readAll) {
				for (int l = 0; l < blocos; l++) {
					// leitura do instante de tempo das medições
					dis.read(b);
					Calendar c0 = null;
					{
						String data = new String(b, 0, POS_DATA_HEADER_SEM_MS);
						try {
							c0 = TimeUtils.date2Calendar(getDate(data));
						} catch (ParseException e1) {
							System.err.println("Formato de data não reconhecido (11): " + data + " no arquivo " + f);
						}
					}

					dis.read(last);
					if (c != null ? (c0 != null ? c0.after(c) : false) : true) {
						// se a medição de depois é de uma data anterior à última, exporta a última
						c = c0;
						out = new boolean[tags.length];
						for (Entry<Integer, Integer> e : posFile2ch.entrySet()) {
							int pos = e.getKey() * bytes;
							out[e.getValue()] = last[pos] == 1;
						}
					}
				}
			} else {
				for (int l = 0; l <= blocos; l++) {
					// lê-se o arquivo até um número l = blocos pois quando se chega no final do
					// arquivo, as funções 'read' não alteram o vetor de bytes, de modo que c == c0,
					// lendo-se a última medição

					// leitura do instante de tempo das medições
					dis.read(b);

					Calendar c0 = null;
					{
						String date = new String(b, 0, POS_DATA_HEADER_SEM_MS);
						try {
							c0 = TimeUtils.date2Calendar(getDate(date));
						} catch (ParseException e1) {
							System.err.println("Formato de data não reconhecido (12): " + date + " no arquivo " + f);
						}
					}

					if (c != null ? (c0 != null ? !c.before(c0) : false) : false) {
						// se a medição de depois é de uma data anterior à última, exporta a última
						out = new boolean[tags.length];
						for (Entry<Integer, Integer> e : posFile2ch.entrySet()) {
							int pos = e.getKey() * bytes;
							out[e.getValue()] = last[pos] == 1;
						}
						break;
					} else
						c = c0;
					dis.read(last);
				}
			}

			if (dis != null)
				dis.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return out;
	}

	// ========================== AUXILIARES ==========================

	public static int readInt(byte[] bs) throws NumberFormatException {
		if (bs[0] == 'b') {
			bs[0] = 0;
			return ByteBuffer.wrap(bs).order(ByteOrder.BIG_ENDIAN).getInt();
		} else
			return Integer.parseInt(new String(bs));
	}

	private static Date getDate(String data) throws ParseException {
		Date out = null;
		try {
			out = HEADER_BR.parse(data);
		} catch (ParseException e) {
		}
		if (out == null) // o mês pode estar no formato americano
			out = HEADER_US.parse(data);
		return out;
	}

	public static void writeInt(DataOutputStream dos, int n) throws IOException {
		if (n < 10_000)
			// com 4 bytes só para para escrever de 0 a 9999
			dos.writeBytes(String.format("%04d", n));
		else {
			dos.writeByte('b');
			byte[] bs = new byte[4];
			ByteBuffer.wrap(bs).order(ORDER).putInt(n);

			// escreve o número de registro em BIG_ENDIAN
			dos.writeByte(bs[2]);
			dos.writeByte(bs[1]);
			dos.writeByte(bs[0]);
		}
	}

	/**
	 * Função que retorna o instante de tempo em que o arquivo HIS foi gerado
	 * 
	 * @param f arquivo de medições
	 * @return instante de tempo em que ele foi criado
	 */
	public static Calendar getRefresh(File f) {
		InputStream is = null;
		try {
			is = new FileInputStream(f);
		} catch (FileNotFoundException | NullPointerException e1) {
			return null;
		}
		DataInputStream dis = new DataInputStream(is);

		Date out = null;
		try {
			// número de registros
			dis.skip(4);

			// ultima atualização
			byte[] b = new byte[POS_ATUALIZACAO];
			dis.read(b);
			{
				String data = new String(b);
				try {
					out = getDate(data);
				} catch (ParseException e) {
					System.err.println("Formato de data não reconhecido (13): " + data + " no arquivo " + f);
				}
			}

			if (dis != null)
				dis.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return TimeUtils.date2Calendar(out);
	}

	/**
	 * Função que retorna o instante de tempo da última medição válida
	 * 
	 * @param c           dia no qual as medições contidas no arquivo foram feitas
	 * @param status inteiro indicando a origem do arquivo de medição
	 * @return instante de tempo da última série de medições válidas
	 */
	public static Calendar getEnd(File f) {
		if (f == null)
			return null;
		InputStream is = null;
		try {
			is = new FileInputStream(f);
		} catch (FileNotFoundException | NullPointerException e1) {
			e1.printStackTrace();
			return null;
		}
		DataInputStream dis = new DataInputStream(is);

		Date out = null;
		try {
			// número de registros
			byte[] b = new byte[4];
			dis.read(b);
			int registros = readInt(b);

			// ultima atualização
			dis.skip(POS_ATUALIZACAO);

			// número de bytes por medição
			int bytes = dis.readByte();

			dis.skip(registros * POS_TAG_INFO);

			// cabeçalho indicando a data e hora
			b = new byte[POS_DATA_HEADER];
			// cada registro tem 9 bytes a cada minuto
			int dadosDoMinuto = bytes * registros;
			// tamanho do bloco do minuto
			int blocoLength = POS_DATA_HEADER + dadosDoMinuto;
			// número de blocos para cada instante de tempo
			int blocos = dis.available() / blocoLength;

			// para todos os minutos do dia
			for (int l = 0; l < blocos; l++) {
				// data

				dis.read(b);

				// leitura do instante de tempo das medições
				Date d = null;
				{
					String data = new String(b, 0, POS_DATA_HEADER_SEM_MS);
					try {
						d = getDate(data);
					} catch (ParseException e) {
						System.err.println("Formato de data não reconhecido (14): " + data + " no arquivo " + f);
					}
				}

				if (out == null ? true : (d == null ? false : d.after(out)))
					out = d;
				dis.skip(dadosDoMinuto);
			}

			if (dis != null)
				dis.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return TimeUtils.date2Calendar(out);
	}

	public static List<String> getTags(String f) {
		return getTags(new File(f));
	}

	/**
	 * Função que retorna a lista das tags de um arquivo de medições .HIS
	 * 
	 * @param f arquivo com as medições
	 * @return lista com todas as tags presentes no arquivo .HIS
	 */
	public static List<String> getTags(File f) {
		if (f == null)
			return null;
		List<String> out = null;
		try {
			DataInputStream dis = new DataInputStream(new FileInputStream(f));

			// número de registros
			byte[] b = new byte[4];
			dis.read(b);
			int registros = readInt(b);

			out = new ArrayList<>(registros);

			// ultima atualização (23) E número de bytes por medição
			// (normalmente '9')
			dis.skip(POS_ATUALIZACAO + 1);

			// descrição dos registro
			b = new byte[POS_TAG_INFO];
			for (int l = 0; l < registros; l++) {
				dis.read(b);
				String s = new String(b, POS_TAG, TAG_SIZE).trim();
				if (!"".equals(s))
					out.add(s);
			}

			dis.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return out;
	}

	/**
	 * Função que carrega para um objeto de {@link RegP registro} todas as medições
	 * dos arquivos HIS contidos numa pasta
	 * 
	 * @param reg       registro onde serão inseridas as medições
	 * @param folder pasta com os arquivos
	 */
	public static Reg readHISfiles(String folder) {
		Reg out = null;
		File[] fs = new File(folder).listFiles();
		for (int i = 0; i < fs.length; i++) {
			String f = fs[i].getAbsolutePath();
			if (f.endsWith(".HIS")) {
				Reg his = readHISfile(f);
				if (out == null)
					out = his;
				else
					out.putAll(his);
			}
		}
		return out;
	}

	/**
	 * Função que carrega para um objeto de {@link RegP registro} todas as medições
	 * de um dia que estão guardados em um arquivo HIS
	 * 
	 * @param his arquivo HIS
	 * @return objeto de registros {@link RegP}
	 */
	public static Reg readHISfile(String his) {
		Reg reg = null;

		// arquivo HIS
		DataInputStream dis = null;
		try {
			dis = new DataInputStream(new FileInputStream(new File(his)));
		} catch (FileNotFoundException | NullPointerException e1) {
			e1.printStackTrace();
		}

		try {
			// número de registros
			byte[] b = new byte[4];
			dis.read(b);
			int registros = readInt(b);

			// ultima atualização (23) E número de bytes por medição
			// (normalmente '9')
			dis.skip(POS_ATUALIZACAO);

			int bytes = dis.readByte();

			// relação de todas as tags contidas no arquivo
			reg = new Reg(registros);
			b = new byte[POS_TAG_INFO];
			for (int l = 0; l < registros; l++) {
				dis.read(b);

				String tag = new String(b, POS_TAG, TAG_SIZE).trim();
				reg.setLabel(l, tag);
			}
			b = new byte[bytes];

			// cabeçalho indicando a data e hora
			byte[] bh = new byte[POS_DATA_HEADER];
			// cada registro tem 9 bytes a cada minuto
			int dadosDoMinuto = bytes * registros;
			// tamanho do bloco do minuto
			int blocoLength = POS_DATA_HEADER + dadosDoMinuto;
			// número de blocos para cada instante de tempo
			int blocos = dis.available() / blocoLength;

			// para todos os minutos do dia
			for (int l = 0; l < blocos; l++) {
				// data
				dis.read(bh);
				// leitura do instante de tempo das medições
				Calendar c = null;
				{
					String data = new String(bh, 0, POS_DATA_HEADER_SEM_MS);

					try {
						c = TimeUtils.date2Calendar(getDate(data));
					} catch (ParseException e) {
						System.err.println("Formato de data não reconhecido (15): " + data + " no arquivo " + his);
					}
				}
				if (c != null) {
					int ci = TimeUtils.toInt(c);

					// dados
					for (int k = 0; k < registros; k++) {
						dis.read(b);

						// **** GRANDEZA ****
						byte[] b1 = Arrays.copyOfRange(b, 0, 4);

						float v = ByteBuffer.wrap(b1).order(ORDER).getFloat();

						reg.put(ci, k, v);
					}
				}
			}
			if (dis != null)
				dis.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		return reg;
	}

	/**
	 * Função que converte o arquivo HIS do histórico do RegHist para uma planilha
	 * do EXCEL
	 * 
	 * @param his caminho do arquivo HIS
	 * @param out caminho da planilha do EXCEL a ser criada (sem a extensão, pois  
	 *                     essa vai ser decidida em função do tamanho do arquivo
	 *            HIS:            arquivos pequenos se tornarão planilhas XLSX,
	 *            arquivos grandes            CSV)
	 */
	public static void his2xls(String his, String out) {
		File f = new File(his);
		boolean csv = f.length() > 5_000_000L;

		// arquivo HIS
		DataInputStream dis = null;
		try {
			dis = new DataInputStream(new FileInputStream(f));
		} catch (FileNotFoundException | NullPointerException e1) {
			e1.printStackTrace();
		}

		// planilha XLSX
		XSSFWorkbook wb = null;
		XSSFSheet sh = null;
		XSSFRow row = null;
		RandomAccessFile raf = null;
		if (csv) {
			try {
				raf = new RandomAccessFile(out + ".csv", "rw");
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		} else {
			wb = new XSSFWorkbook();
			sh = wb.createSheet("Medições");
		}

		try {
			// número de registros
			byte[] b = new byte[4];
			dis.read(b);
			int registros = readInt(b);

			// ultima atualização (23) E número de bytes por medição
			// (normalmente '9')
			dis.skip(POS_ATUALIZACAO);

			int bytes = dis.readByte();

			// relação de todas as tags contidas no arquivo
			if (!csv)
				row = sh.createRow(0);

			b = new byte[POS_TAG_INFO];
			for (int l = 0; l < registros; l++) {
				dis.read(b);

				String tag = new String(b, POS_TAG, TAG_SIZE).trim();
				if (csv)
					raf.writeBytes(";" + tag);
				else
					row.createCell(l + 1).setCellValue(tag);
			}
			b = new byte[bytes];

			// cabeçalho indicando a data e hora
			byte[] bh = new byte[POS_DATA_HEADER];
			// cada registro tem 9 bytes a cada minuto
			int dadosDoMinuto = bytes * registros;
			// tamanho do bloco do minuto
			int blocoLength = POS_DATA_HEADER + dadosDoMinuto;
			// número de blocos para cada instante de tempo
			int blocos = dis.available() / blocoLength;

			// para todos os minutos do dia
			for (int l = 0; l < blocos; l++) {
				// data
				dis.read(bh);

				// leitura do instante de tempo das medições
				Date date = null;
				{
					String data = new String(bh, 0, POS_DATA_HEADER_SEM_MS);
					try {
						date = getDate(data);
					} catch (ParseException e) {
						System.err.println("Formato de data não reconhecido (16): " + data + " no arquivo " + his);
					}
				}

				if (csv)
					raf.writeBytes(String.format("\r\n%1$td-%1$tm-%1$tY %1$tT", date));
				else {
					row = sh.createRow(l + 1);
					row.createCell(0).setCellValue(date);
				}

				// dados
				for (int k = 0; k < registros; k++) {
					dis.read(b);

					// **** GRANDEZA ****
					byte[] b1 = Arrays.copyOfRange(b, 0, 4);

					float v = ByteBuffer.wrap(b1).order(ORDER).getFloat();

					if (csv) {
						if (Float.isNaN(v))
							raf.writeBytes(";-");
						else
							raf.writeBytes(String.format(";%g", v));
					} else {
						if (Float.isNaN(v))
							row.createCell(k + 1).setCellValue("-");
						else
							row.createCell(k + 1).setCellValue(v);
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
			if (csv)
				raf.close();
			else {
				FileOutputStream fos = new FileOutputStream(out + ".xlsx");
				wb.write(fos);
				fos.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Função que faz a fusão de dois arquivos HIS
	 * 
	 * @param base           arquivo de base
	 * @param comp           arquivo complementar
	 * @param folderOut diretório onde o novo arquivo será guardado (o nome do      
	 *                             arquivo de saída será formulado em função do dia
	 *                  dos                  registros nele guardados)
	 */
	public static void mergeHISfiles(String base, String comp, String folderOut) {
		// arquivo HIS
		Reg reg1 = readHISfile(base);
		Reg reg2 = readHISfile(comp);

		// alargar, se necessário
		if (reg1.length() < reg2.length()) {
			reg1.setRegs(reg2.length());

			String[] tags1 = reg1.getLabels();
			String[] tags2 = reg2.getLabels();
			tags1 = Arrays.copyOf(tags1, tags2.length);
			for (int i = 0; i < tags1.length; i++) {
				String s = tags2[i];
				if (s != null)
					tags1[i] = s;
			}
			reg1.setLabels(tags1);
		}

		for (Entry<Integer, float[]> ic : reg1.entrySet()) {
			float[] values1 = ic.getValue();
			float[] values2 = reg2.get(ic.getKey());

			if (values2 != null) {
				for (int i = 0; i < values1.length; i++) {
					float f2 = values2[i];
					if (!Float.isNaN(f2))
						values1[i] = f2;
				}
			}
		}

		writeFile(folderOut, reg1);
	}

	// =================== ESCREVER ARQUIVOS NO FORMATO HIS ===================

	/**
	 * Função que escreve os dados contidos num {@link Reg registro} em um ou mais
	 * arquivos HIS
	 * 
	 * @param folder pasta de destino do arquivo (os nomes dos arquivos são        
	 *                     definidos em função do dia)
	 * @param reg       registro contendo dos dados
	 */
	public static void writeFile(String folder, Reg reg) {
		DataOutputStream dos = null;
		Calendar c = null;
		String[] tags = reg.getLabels();

		// criar folder, se este não existir
		File f = new File(folder);
		if (!f.exists())
			f.mkdir();

		try {
			for (Entry<Integer, float[]> e : reg.entrySet()) {
				Calendar d = TimeUtils.toCalendar(e.getKey());

				if (!TimeUtils.isSameDay(c, d)) {
					// se não é mais o mesmo dia, criar novo arquivo

					// se houver algum arquivo aberto (i.e., se este não for o primeiro) fecha-o
					if (dos != null)
						dos.close();

					dos = new DataOutputStream(
							new FileOutputStream(String.format(FILENAME_FORMAT, folder + "/", 'P', d)));

					int meds = reg.length();
					writeInt(dos, meds);

					// data de criação do arquivo (23 bytes)
					dos.writeBytes(String.format(DATE_FORMAT, Calendar.getInstance()));
					// número de bytes por medição (1 byte)
					dos.write(5);

					for (int i = 0; i < tags.length; i++)
						dos.writeBytes(String.format("%025d%-30s\t", 0,
								tags[i].substring(0, Math.min(TAG_SIZE - 1, tags[i].length()))));

					// novo dia
					c = d;
				}

				// data e hora (24 bytes)
				dos.writeBytes(String.format(DATE_FORMAT + " ", d));
				// medições
				byte[] b = new byte[4];
				float[] fs = e.getValue();
				for (int i = 0; i < tags.length; i++) {
					ByteBuffer.wrap(b).order(ORDER).putFloat(fs[i]);
					dos.write(b);
					dos.write(Float.isNaN(fs[i]) ? ERRO : 0);
				}
			}
			if (dos != null)
				dos.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	public static void writeFile(String folder, RegS reg) {
		DataOutputStream dos = null;
		Calendar c = null;
		String[] tags = reg.getLabels();

		// criar folder, se este não existir
		File f = new File(folder);
		if (!f.exists())
			f.mkdir();

		try {
			for (Entry<Integer, byte[]> e : reg.entrySet()) {
				Calendar d = TimeUtils.toCalendar(e.getKey());

				if (!TimeUtils.isSameDay(c, d)) {
					// se não é mais o mesmo dia, criar novo arquivo

					// se houver algum arquivo aberto (i.e., se este não for o primeiro) fecha-o
					if (dos != null)
						dos.close();

					dos = new DataOutputStream(
							new FileOutputStream(String.format(FILENAME_FORMAT, folder + "/", 'P', d)));

					int meds = reg.length();
					writeInt(dos, meds);

					// data de criação do arquivo (23 bytes)
					dos.writeBytes(String.format(DATE_FORMAT, Calendar.getInstance()));
					// número de bytes por medição (1 byte)
					dos.write(reg.getDataSize());

					for (int i = 0; i < tags.length; i++)
						dos.writeBytes(String.format("%025d%-30s\t", 0,
								tags[i].substring(0, Math.min(TAG_SIZE - 1, tags[i].length()))));

					// novo dia
					c = d;
				}

				// data e hora (24 bytes)
				dos.writeBytes(String.format(DATE_FORMAT + " ", d));
				// medições
				for (int i = 0; i < tags.length; i++)
					dos.write(e.getValue());
			}
			if (dos != null)
				dos.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	/**
	 * Função que escreve um arquivo .HIS vazio
	 * 
	 * @param file   caminho do arquivo .HIS
	 * @param c0       instante de tempo do dia
	 * @param tags   vetores de tags
	 * @param bytes número de bytes por medição (4 para float's, 8 para doubles; o  
	 *                         RegHist usa 9: dois float e uma máscara de dados)
	 */
	public static void writeEmptyFile(File file, Calendar c0, String[] tags, int bytes) {
		Calendar c = (Calendar) c0.clone();
		c.set(Calendar.HOUR_OF_DAY, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		// reuca um dia (para dizer que está o arquivo inteiro atrasado)
		c.add(Calendar.DAY_OF_MONTH, -1);

		try {
			DataOutputStream dos = new DataOutputStream(new FileOutputStream(file));

			writeInt(dos, tags.length);

			// data de criação do arquivo (23 bytes)
			dos.writeBytes(String.format(DATE_FORMAT, Calendar.getInstance()));
			// número de bytes por medição (1 byte)
			dos.write(bytes);

			for (int j = 0; j < tags.length; j++)
				dos.writeBytes(String.format("%025d%-30s\t", 0, tags[j]));

			byte[] z = new byte[bytes];
			for (int i = 0; i < MIN_DAY; i++) {
				// data e hora (24 bytes)
				dos.writeBytes(String.format(DATE_FORMAT + " ", c));

				for (int j = 0; j < tags.length; j++)
					dos.write(z);

				c.add(Calendar.MINUTE, 1);
			}

			dos.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	/**
	 * Função que reescreve uma parte do arquivo HIS, substituindo
	 * <strong>todas</strong> as medições de um dado instante por outras. A data e
	 * hora também será substituída, sendo que o arquivo final pode eventualmente
	 * conter instantes de tempo de datas diferentes (que normalmente não acontece
	 * nos arquivos P do histórico, mas sempre acontece nos arquivos da semana
	 * atual, sendo que estes geralmente trazem medições de dois dias contíguos)
	 * 
	 * @param f           arquivo a ser editado
	 * @param c           instante de tempo (horas e minutos) onde entrará as novas
	 *                             medições
	 * @param values vetor com os todos os valores do instante de tempo dado
	 * @param bytes   bytes por medição
	 */
	public static void rewriteFile(File f, Calendar c, float[] values, int bytes) {
		if (values == null)
			return;

		int lines = 60 * c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE);
		try {
			RandomAccessFile raf = new RandomAccessFile(f, "rw");

			// número de registros (4)
			byte[] b = new byte[4];
			raf.read(b);
			int registros = readInt(b);
			if (registros != values.length) {
				raf.close();
				throw new IllegalAccessError(String.format("Número diferente de valores do de tags"));
			}

			// ultima atualização (23)
			raf.writeBytes(String.format(DATE_FORMAT, Calendar.getInstance()));

			// número de bytes por medição (normalmente '9')
			int bytes0 = raf.readByte();
			if (bytes0 != bytes) {
				raf.close();
				throw new IllegalAccessError("Número diferente de bytes por medição" + bytes0);
			}

			// pula as tags e os horários de antes da data indicada
			raf.seek(HEADER_SIZE + POS_TAG_INFO * registros + (POS_DATA_HEADER + bytes * registros) * lines);

			// data e hora (24 bytes)
			raf.writeBytes(String.format(DATE_FORMAT + " ", c));
			// medições
			b = new byte[bytes];
			for (int j = 0; j < registros; j++) {
				// usa somente os 4 primeiros bytes para escrever o float (os demais bytes-4 são
				// deixados com valores nulos)
				ByteBuffer.wrap(b).order(ORDER).putFloat(values[j]);
				raf.write(b);
			}

			raf.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	/**
	 * Função que reescreve uma parte do arquivo HIS, substituindo
	 * <strong>algumas</strong> medições de um dado instante por outras.
	 * 
	 * @param f
	 * @param c           instante de tempo (horas e minutos) onde entrará as novas
	 *                             medições
	 * @param pos
	 * @param values vetor com os valores do instante de tempo dado
	 * @param bytes   bytes por medição
	 * @param date     <code>true</code> para substituir a data, <code>false</code>
	 *                             para deixar a data anterior
	 */
	public static void rewriteFile(File f, Calendar c, int[] pos, float[] values, int bytes, boolean date) {
		if (values == null)
			return;

		int lines = 60 * c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE);
		try {
			RandomAccessFile raf = new RandomAccessFile(f, "rw");

			// número de registros (4)
			byte[] b = new byte[4];
			raf.read(b);
			int registros = readInt(b);
			if (registros != values.length) {
				raf.close();
				throw new IllegalAccessError(String.format("Número diferente de valores do de tags"));
			}

			// ultima atualização (23)
			raf.writeBytes(String.format(DATE_FORMAT, Calendar.getInstance()));

			// número de bytes por medição (normalmente '9')
			int bytes0 = raf.readByte();
			if (bytes0 != bytes) {
				raf.close();
				throw new IllegalAccessError("Número diferente de bytes por medição" + bytes0);
			}

			// pula as tags e os horários de antes da data indicada
			int p = HEADER_SIZE + POS_TAG_INFO * registros + (POS_DATA_HEADER + bytes * registros) * lines;
			if (date) {
				// data e hora (24 bytes)
				raf.seek(p);
				raf.writeBytes(String.format(DATE_FORMAT + " ", c));
			}
			p += POS_DATA_HEADER;
			// medições
			b = new byte[bytes];
			for (int j = 0; j < pos.length; j++) {
				raf.seek(p + pos[j] * bytes);
				// usa somente os 4 primeiros bytes para escrever o float (os
				// demais bytes-4 são deixados com valores nulos)
				ByteBuffer.wrap(b).order(ORDER).putFloat(values[j]);
				raf.write(b);
			}

			raf.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	/**
	 * Função que reseta o arquivo de medições, pondo todos as medições nulas e com
	 * a data de um dado dia
	 * 
	 * @param f arquivo a ser reescrito
	 * @param c data de todas as medições
	 */
	public static void eraseFile(File f, Calendar c) {
		c = (Calendar) c.clone();
		c.set(Calendar.HOUR_OF_DAY, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		try {
			RandomAccessFile raf = new RandomAccessFile(f, "rw");

			// número de registros (4)
			byte[] b = new byte[4];
			raf.read(b);
			int registros = readInt(b);

			// ultima atualização (23)
			raf.writeBytes(String.format(DATE_FORMAT, Calendar.getInstance()));

			// número de bytes por medição (normalmente '9')
			int bytes = raf.readByte();

			// pula as tags (i.e., mantém o cabeçalho)
			raf.seek(HEADER_SIZE + POS_TAG_INFO * registros);

			b = new byte[bytes];
			for (int i = 0; i < MIN_DAY; i++) {
				// data e hora (24 bytes)
				raf.writeBytes(String.format(DATE_FORMAT + " ", c));

				for (int j = 0; j < registros; j++)
					raf.write(b);

				c.add(Calendar.MINUTE, 1);
			}
			raf.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	/**
	 * Função que transfere todas as medições de um arquivo para um dado instante
	 * para um outro arquivo de medições
	 * 
	 * @param from arquivo de onde sairão as medições
	 * @param to     arquivo de destino das medições
	 * @param c       instante de tempo das medições consideradas
	 * @param pos   posição no arquivo de destino onde as medições serão inseridas  
	 *                       (sobreescrevendo as lá existentes)
	 */
	public static void transfer(File from, File to, Calendar c, int pos) {

		// arquivo HIS
		DataInputStream dis = null;
		try {
			dis = new DataInputStream(new FileInputStream(from));
		} catch (FileNotFoundException | NullPointerException e1) {
			e1.printStackTrace();
		}

		float[] values = null;

		try {
			// número de registros
			byte[] b = new byte[4];
			dis.read(b);
			int registros = readInt(b);
			values = new float[registros];

			// ultima atualização (23) E número de bytes por medição
			// (normalmente '9')
			dis.skip(POS_ATUALIZACAO);
			int bytes = dis.readByte();

			// pular tags
			dis.skip(registros * POS_TAG_INFO);

			// cabeçalho indicando a data e hora
			byte[] bh = new byte[POS_DATA_HEADER];
			// cada registro tem 9 bytes a cada minuto
			int dadosDoMinuto = bytes * registros;
			// tamanho do bloco do minuto
			int blocoLength = POS_DATA_HEADER + dadosDoMinuto;
			// número de blocos para cada instante de tempo
			int blocos = dis.available() / blocoLength;

			// instante procurado
			int c0 = TimeUtils.toInt(c);
			// vetor de leitura
			b = new byte[bytes];

			// para todos os minutos do dia
			for (int l = 0; l < blocos; l++) {
				// data
				dis.read(bh);

				// leitura do instante de tempo das medições

				Calendar c1 = null;
				{
					String data = new String(bh, 0, POS_DATA_HEADER_SEM_MS);
					try {
						c1 = TimeUtils.date2Calendar(getDate(data));
					} catch (ParseException e) {
						System.err.println("Formato de data não reconhecido (17): " + data + " no arquivo " + from);
					}

				}

				if (c1 == null)
					dis.skip(dadosDoMinuto);

				int ci = TimeUtils.toInt(c1);

				if (ci == c0) {
					for (int k = 0; k < registros; k++) {
						dis.read(b);
						byte[] b1 = Arrays.copyOfRange(b, 0, 4);
						values[k] = ByteBuffer.wrap(b1).order(ORDER).getFloat();
					}
					break;
				} else
					dis.skip(dadosDoMinuto);
			}
			if (dis != null)
				dis.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		// ===================================================================

		try {
			RandomAccessFile raf = new RandomAccessFile(to, "rw");

			// número de registros (4)
			byte[] b = new byte[4];
			raf.read(b);
			int registros = readInt(b);
			if (registros != values.length) {
				raf.close();
				throw new IllegalAccessError(String.format("Número diferente de tags entre os dois arquivos"));
			}

			// ultima atualização (23)
			raf.writeBytes(String.format(DATE_FORMAT, Calendar.getInstance()));

			// número de bytes por medição (normalmente '9')
			int bytes = raf.readByte();

			// pula as tags e os horários de antes da data indicada
			raf.seek(HEADER_SIZE + POS_TAG_INFO * registros + (POS_DATA_HEADER + bytes * registros) * pos);
			// data e hora (24 bytes)
			raf.writeBytes(String.format(DATE_FORMAT + " ", c));

			// medições
			b = new byte[bytes];
			for (int j = 0; j < values.length; j++) {
				// usa somente os 4 primeiros bytes para escrever o float (os
				// demais bytes-4 são deixados com valores nulos)
				ByteBuffer.wrap(b).order(ORDER).putFloat(values[j]);
				raf.write(b);
			}

			raf.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	/**
	 * Função que impõe a to
	 * 
	 * @param f
	 * @param c
	 * @param h
	 * @param m
	 */
	public static void fixDate(File f, Calendar c, int h, int m) {
		try {
			RandomAccessFile raf = new RandomAccessFile(f, "rw");

			// número de registros (4)
			byte[] b = new byte[4];
			raf.read(b);
			int registros = readInt(b);

			// ultima atualização (23)
			raf.writeBytes(String.format(DATE_FORMAT, Calendar.getInstance()));

			// número de bytes por medição (normalmente '9')
			int bytes = raf.readByte();
			int blocoLength = POS_DATA_HEADER + bytes * registros;

			// pula as tags
			int p = HEADER_SIZE + POS_TAG_INFO * registros;
			raf.seek(p);

			Calendar c0 = null;
			if (c != null) {
				// caso tenha se indicado a data que prevalecerá no arquivo
				c0 = (Calendar) c.clone();
				c0.set(Calendar.HOUR_OF_DAY, 0);
				c0.set(Calendar.MINUTE, 0);
				c0.set(Calendar.SECOND, 0);
			} else {
				// caso não tenha sido informada a data referência
				byte[] bh = new byte[POS_DATA_HEADER];
				raf.read(bh);

				{
					String data = new String(bh, 0, POS_DATA_HEADER_SEM_MS);
					try {
						c0 = TimeUtils.date2Calendar(getDate(data));
					} catch (ParseException e) {
						System.err.println(
								"Formato de data não reconhecido (18): " + data + " no arquivo " + f.getAbsolutePath());
					}
				}
				if (c0 == null) {
					raf.close();
					return;
				}

				p += blocoLength;
				raf.seek(p);

				c0.add(Calendar.MINUTE, 1);
			}

			while (true) {
				// para cada um dos minutos do dia
				raf.writeBytes(String.format(DATE_FORMAT + " ", c0));

				p += blocoLength;
				raf.seek(p);

				if (c0.get(Calendar.HOUR_OF_DAY) == h && c0.get(Calendar.MINUTE) == m)
					break;
				else
					c0.add(Calendar.MINUTE, 1);
			}

			raf.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}
}