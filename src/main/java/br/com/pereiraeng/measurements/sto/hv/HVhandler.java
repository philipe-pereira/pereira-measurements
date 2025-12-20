package br.com.pereiraeng.measurements.sto.hv;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Iterator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;

import br.com.pereiraeng.math.swing.chart.time.TimePeriod;
import br.com.pereiraeng.math.timeseries.RegP;
import br.com.pereiraeng.math.timeseries.esp.MedDataType;
import br.com.pereiraeng.math.timeseries.esp.RegSP;
import br.com.pereiraeng.math.timeseries.unit.MedH;
import br.com.pereiraeng.math.timeseries.unit.MedV;
import br.com.pereiraeng.math.timeseries.unit.Meds;
import br.com.pereiraeng.measurements.MedHandler;
import br.com.pereiraeng.physics.Grandeza;
import br.com.pereiraeng.core.TimeUtils;
import br.com.pereiraeng.core.Tag;

/**
 * Classe das funções que permitem manipular uma base de dados de medições HV
 * (medições periódicas, por ponto e por instante de tempo)
 * 
 * @author Philipe PEREIRA
 * @version September 18th, 2020
 */
public class HVhandler extends MedHandler {

	private final File folder;

	public HVhandler(File folder) {
		this.folder = folder;
	}

	@Override
	public MedH get(Calendar c) {
		return HVhandler.readFileH(TimeUtils.toInt(c), folder);
	}

	@Override
	public MedV get(Tag c) {
		return HVhandler.readFileV(c.toString(), folder);
	}

	@Override
	public RegSP get(TimePeriod tp) {
		// implementações vazias: HV não se usa assim
		return null;
	}

	@Override
	public RegSP get(Tag[] c) {
		// implementações vazias: HV não se usa assim
		return null;
	}

	private static final String FOLDER_H = "/h/", FOLDER_V = "/v/";

	private static final String TERM_H = "mh", TERM_HH = "mhh", TERM_V = "mv", TERM_VH = "mvh";

	// ============================= VERTICAL =============================
	// cada arquivo é um ponto e cada série de dados no arquivo é dada para um
	// instante de tempo

	/**
	 * Função que cria um diretório (caso já não exista) e escreve na pasta
	 * {@link #FOLDER_V} os arquivos verticais (i.e., todas as medições horárias de
	 * um ponto num arquivo)
	 * 
	 * @param folder diretório onde serão salvadas as medições
	 * @param meds   tabela que associa para cada ponto um {@link RegP registro
	 *               horário periódico}
	 */
	public static void writeFilesV(File folder, Map<String, RegP> meds) {
		Iterator<RegP> it = meds.values().iterator();
		String[] labels = null;
		int begin = 0;
		int end = 0;
		short step = 0;
		int n = 0;
		while (it.hasNext()) {
			RegP sample = it.next();
			if (labels == null) {
				begin = sample.firstKey();
				end = sample.lastKey();
				step = (short) (sample.getFreq() * 60);
				n = sample.size();

				if ((n - 1) != (end - begin) / step)
					it.remove();
				else {
					labels = sample.getLabels();
					break;
				}
			}
		}
		Grandeza[] as = new Grandeza[labels.length];
		for (int i = 0; i < labels.length; i++)
			as[i] = Grandeza.values()[Integer.parseInt(labels[i])];
		writeFilesV(folder, meds, new Vheader(begin, end, step, n, (byte) 4, as));
	}

	/**
	 * Função que cria um diretório (caso já não exista) e escreve na pasta
	 * {@link #FOLDER_V} os arquivos verticais (i.e., todas as medições horárias de
	 * um ponto num arquivo)
	 * 
	 * @param folder diretório onde serão salvadas as medições
	 * @param meds   tabela que associa para cada ponto um {@link RegP registro
	 *               horário periódico}
	 * @param vh     objeto que representa o cabeçalho dos arquivos verticais
	 */
	public static void writeFilesV(File folder, Map<String, RegP> meds, Vheader vh) {
		File fv = new File(folder.getAbsolutePath() + FOLDER_V);

		if (!folder.exists()) {
			folder.mkdir();
			fv.mkdir();
		} else {
			if (fv.exists()) { // clear
				File[] fs = fv.listFiles();
				for (File f : fs)
					f.delete();
			} else
				fv.mkdir();
		}

		if (!fv.isDirectory())
			throw new IllegalArgumentException("não foi possível criar o diretório");

		// ==========================

		try { // header
			RandomAccessFile raf = new RandomAccessFile(
					new File(fv.getAbsolutePath() + "/" + folder.getName() + "." + TERM_VH), "rw");
			vh.write(raf);
			raf.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		List<RandomAccessFile> p2raf = new ArrayList<>(meds.size());
		for (Entry<String, RegP> e : meds.entrySet()) { // um arquivo por ponto
			try {
				RandomAccessFile raf = new RandomAccessFile(
						new File(fv.getAbsolutePath() + "/" + e.getKey() + "." + TERM_V), "rw");
				p2raf.add(raf);
			} catch (FileNotFoundException ex) {
				ex.printStackTrace();
			}
		}

		byte[] bytes = new byte[vh.getBpm()];
		int j = 0;
		for (RegP regp : meds.values()) { // p pontos
			RandomAccessFile raf = p2raf.get(j++);
			for (float[] vs : regp.values()) { // n instantes de tempo
				for (int i = 0; i < vs.length; i++) { // m medições
					ByteBuffer.wrap(bytes).putFloat(vs[i]);
					try {
						raf.write(bytes);
					} catch (IOException ex) {
						ex.printStackTrace();
					}
				}
			}
		}

		// closing
		for (RandomAccessFile raf : p2raf) {
			try {
				raf.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Função que converte um registro vertical em horizontal
	 * 
	 * @param folder diretório onde serão salvadas as medições
	 */
	public static void v2h(File folder) {
		if (!folder.exists())
			return;
		else if (!folder.isDirectory())
			return;

		File fv = new File(folder.getAbsolutePath() + FOLDER_V);
		if (!fv.exists())
			return;
		else if (!fv.isDirectory())
			return;

		// ----------

		File fh = new File(folder.getAbsolutePath() + FOLDER_H);

		if (!folder.exists()) {
			folder.mkdir();
			fh.mkdir();
		} else {
			if (fh.exists()) { // clear
				File[] fs = fh.listFiles();
				for (File f : fs)
					f.delete();
			} else
				fh.mkdir();
		}

		// ======================

		try {
			// header
			Vheader vh = getVheader(folder);

			List<RandomAccessFile> p2raf = new ArrayList<>(vh.getN());
			int step = vh.getStep();
			int n = vh.getN();
			int ci = vh.getBegin();
			for (int i = 0; i < n; i++) {
				p2raf.add(new RandomAccessFile(new File(fh.getAbsolutePath() + "/" + ci + "." + TERM_H), "rw"));
				ci += step;
			}

			// analisar diretório de medidores
			List<String> pl = getPoints(folder);

			// transferência
			byte[] bytes = new byte[vh.getBpm() * vh.getM()];
			for (String point : pl) {
				RandomAccessFile rafv = new RandomAccessFile(new File(fv + "/" + point + "." + TERM_V), "r");
				for (int i = 0; i < n; i++) { // n instantes de tempo
					rafv.read(bytes); // para m medições
					p2raf.get(i).write(bytes);
				}
				rafv.close();
			}

			Hheader hh = new Hheader(vh, pl.toArray(new String[pl.size()]));
			RandomAccessFile raf = new RandomAccessFile(
					new File(fh.getAbsolutePath() + "/" + folder.getName() + "." + TERM_HH), "rw");
			hh.write(raf);
			raf.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Função que retorna a lista com a designação dos medidores
	 * 
	 * @param folder diretório contendo as pastas {@link #FOLDER_H 'h'} e
	 *               {@link #FOLDER_V 'v'}
	 * @return lista de tamanho P com as designações dos medidores
	 */
	public static List<String> getPoints(File folder) {
		if (!folder.exists())
			return null;
		else if (!folder.isDirectory())
			return null;

		File fv = new File(folder.getAbsolutePath() + FOLDER_V);
		if (!fv.exists())
			return null;
		else if (!fv.isDirectory())
			return null;

		// ======================

		List<String> out = new LinkedList<>();
		Path dir = FileSystems.getDefault().getPath(fv.getAbsolutePath());
		try {
			DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*." + TERM_V);
			for (Path path : stream) { // para p pontos
				String point = path.getFileName().toString();
				out.add(point.substring(0, point.length() - 3));
			}
			stream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return out;
	}

	/**
	 * Função que lê as medições salvas em um registro vertical
	 * 
	 * @param fv    diretório contendo as pastas {@link #FOLDER_H 'h'} e
	 *               {@link #FOLDER_V 'v'}
	 * @param point ponto ao qual se deseja ter acesso às medições
	 * @return registro horário de medições
	 */
	public static RegSP readFileV(File fv, String point) {
		MedV mv = readFileV(point, fv);

		int begin = TimeUtils.toInt(mv.getTime());
		short step = mv.getStep();
		int end = mv.getEnd();
		int n = (end - begin) / step + 1;

		MedDataType[] dataTypes = mv.getDataTypes();
		int bpm = MedDataType.getDataSize(dataTypes);

		int m = mv.getM();
		RegSP out = new RegSP(m, null, new String[] { point }, mv.getStep() / 60, dataTypes);

		byte[] bs = mv.getValues();
		if (bs != null) {
			int ci = begin;
			final int sb = bpm * m;
			for (int i = 0; i <= n; i++) {
				byte[] bs0 = Arrays.copyOfRange(bs, i * sb, (i + 1) * sb);
				out.put(ci, bs0);
				ci += step;
			}
		}
		return out;
	}

	/**
	 * 
	 * @param point  ponto ao qual se deseja ter acesso às medições
	 * @param folder diretório contendo as pastas {@link #FOLDER_H 'h'} e
	 *               {@link #FOLDER_V 'v'}
	 * @return
	 */
	private static MedV readFileV(String point, File folder) {
		if (!folder.exists())
			return null;
		else if (!folder.isDirectory())
			return null;

		File fv = new File(folder.getAbsolutePath() + FOLDER_V);
		if (!fv.exists())
			return null;
		else if (!fv.isDirectory())
			return null;

		// ======================

		// header
		Vheader vh = getVheader(folder);

		// content
		int start = vh.getBegin();
		short step = vh.getStep();
		int end = vh.getEnd();

		MedV out = new MedV(point, TimeUtils.toCalendar(start), step, end, vh.getDataTypes(), vh.getM());

		File f = new File(fv.getAbsoluteFile() + "/" + point + "." + TERM_V);
		if (f.exists()) {
			try {
				RandomAccessFile raf = new RandomAccessFile(f, "r");

				byte[] bs = new byte[vh.getBpm() * vh.getN() * vh.getM()];
				raf.read(bs);
				out.setValues(bs);

				raf.close();
				return out;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return out;
	}

	/**
	 * Função que retorna o cabeçalho das medições horizontais
	 * 
	 * @param folder diretório contendo as pastas {@link #FOLDER_H 'h'} e
	 *               {@link #FOLDER_V 'v'}
	 * @return cabeçalho das medições verticais
	 */
	public static Vheader getVheader(File folder) {
		return (Vheader) getHeader(folder, true);
	}

	// ============================= HORIZONTAL =============================
	// cada arquivo é um instante de tempo e cada série de dados no arquivo é dada
	// para um dado ponto

	/**
	 * Função que cria um diretório (caso já não exista) e escreve na pasta
	 * {@link #FOLDER_H} os arquivos horizontais (i.e., todos os pontos num dado
	 * arquivo)
	 * 
	 * @param folder diretório onde serão salvadas as medições
	 * @param meds   medições
	 * @param m      número de grandezas por ponto por horário
	 */
	public static void writeFileH(File folder, Map<Integer, Map<String, Meds>> meds, int m) {
		Map<String, Meds> sample = meds.values().iterator().next();
		String[] points = sample.keySet().toArray(new String[sample.size()]);
		writeFileH(folder, meds, new Hheader(points, (byte) 4, new Grandeza[m]));
	}

	/**
	 * Função que cria um diretório (caso já não exista) e escreve na pasta
	 * {@link #FOLDER_H} os arquivos horizontais (i.e., todos os pontos num dado
	 * arquivo)
	 * 
	 * @param folder diretório contendo as pastas {@link #FOLDER_H 'h'} e
	 *               {@link #FOLDER_V 'v'}
	 * @param meds   medições a serem escritas
	 * @param hh     objeto que representa o cabeçalho dos arquivos horizontais
	 */
	public static void writeFileH(File folder, Map<Integer, Map<String, Meds>> meds, Hheader hh) {
		File fh = new File(folder.getAbsolutePath() + FOLDER_H);

		if (!folder.exists()) {
			folder.mkdir();
			fh.mkdir();
		} else {
			if (fh.exists()) { // clear
				File[] fs = fh.listFiles();
				for (File f : fs)
					f.delete();
			} else
				fh.mkdir();
		}

		if (!fh.isDirectory())
			throw new IllegalArgumentException("não foi possível criar o diretório");

		// ==========================

		try { // header
			RandomAccessFile raf = new RandomAccessFile(
					new File(fh.getAbsolutePath() + "/" + folder.getName() + "." + TERM_HH), "rw");
			hh.write(raf);
			raf.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		List<RandomAccessFile> ci2raf = new ArrayList<>(meds.size());
		for (Integer ci : meds.keySet()) { // um arquivo por horário
			try {
				RandomAccessFile raf = new RandomAccessFile(new File(fh.getAbsolutePath() + "/" + ci + "." + TERM_H),
						"rw");
				ci2raf.add(raf);
			} catch (FileNotFoundException ex) {
				ex.printStackTrace();
			}
		}

		byte[] bytes = new byte[hh.getBpm()];
		int i = 0;
		String[] points = hh.getPoints();
		for (Map<String, Meds> l2m : meds.values()) { // n instantes de tempo
			RandomAccessFile raf = ci2raf.get(i++);
			for (int j = 0; j < points.length; j++) { // p pontos
				Meds m = l2m.get(points[j]);
				for (int k = 0; k < m.length(); k++) { // m medições
					ByteBuffer.wrap(bytes).putFloat(m.getValue(k));
					if (bytes.length > 4)
						bytes[4] = m.getMask(k);
					try {
						raf.write(bytes);
					} catch (IOException ex) {
						ex.printStackTrace();
					}
				}
			}
		}

		// closing
		for (RandomAccessFile raf : ci2raf) {
			try {
				raf.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Função que converte um registro horizontal em vertical
	 * 
	 * @param folder diretório contendo as pastas {@link #FOLDER_H 'h'} e
	 *               {@link #FOLDER_V 'v'}
	 */
	public static void h2v(File folder) {
		if (!folder.exists())
			return;
		else if (!folder.isDirectory())
			return;

		File fh = new File(folder.getAbsolutePath() + FOLDER_H);
		if (!fh.exists())
			return;
		else if (!fh.isDirectory())
			return;

		// ----------

		File fv = new File(folder.getAbsolutePath() + FOLDER_V);

		if (!folder.exists()) {
			folder.mkdir();
			fv.mkdir();
		} else {
			if (fv.exists()) { // clear
				File[] fs = fv.listFiles();
				for (File f : fs)
					f.delete();
			} else
				fv.mkdir();
		}

		// ======================

		try {
			// header
			Hheader hh = getHheader(folder);

			String[] points = hh.getPoints();
			List<RandomAccessFile> p2raf = new ArrayList<>(points.length);
			for (int i = 0; i < points.length; i++)
				p2raf.add(new RandomAccessFile(new File(fv.getAbsolutePath() + "/" + points[i] + "." + TERM_V), "rw"));

			// analisar diretório e ordenar instantes de tempo
			TreeSet<Integer> cs = getInstants(folder);

			// transferência
			byte[] bytes = new byte[hh.getBpm() * hh.getM()];
			for (Integer ci : cs) { // n instantes de tempo
				RandomAccessFile rafh = new RandomAccessFile(new File(fh + "/" + ci + "." + TERM_H), "r");
				for (int i = 0; i < points.length; i++) { // para p pontos
					rafh.read(bytes); // para m medições
					p2raf.get(i).write(bytes);
				}
				rafh.close();
			}

			int begin = cs.first();
			int end = cs.last();
			int n = cs.size();
			short step = (short) ((end - begin) / (n - 1));

			Vheader vh = new Vheader(hh, begin, end, (short) step, n);
			RandomAccessFile raf = new RandomAccessFile(
					new File(fv.getAbsolutePath() + "/" + folder.getName() + "." + TERM_VH), "rw");
			vh.write(raf);
			raf.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 
	 * @param folder diretório contendo as pastas {@link #FOLDER_H 'h'} e
	 *               {@link #FOLDER_V 'v'}
	 * @return conjunto ordenado de tamanho N com os instantes de tempo em que há
	 *         medições
	 */
	public static TreeSet<Integer> getInstants(File folder) {
		if (!folder.exists())
			return null;
		else if (!folder.isDirectory())
			return null;

		File fh = new File(folder.getAbsolutePath() + FOLDER_H);
		if (!fh.exists())
			return null;
		else if (!fh.isDirectory())
			return null;

		// ======================

		TreeSet<Integer> out = new TreeSet<>();
		Path dir = FileSystems.getDefault().getPath(fh.getAbsolutePath());
		try {
			DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*." + TERM_H);
			for (Path path : stream) { // n instantes de tempo
				String cis = path.getFileName().toString();
				out.add(Integer.parseInt(cis.substring(0, cis.length() - 3)));
			}
			stream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return out;
	}

	/**
	 * Função que lê as medições salvas em um registro horizontal
	 * 
	 * @param folder diretório contendo as pastas {@link #FOLDER_H 'h'} e
	 *               {@link #FOLDER_V 'v'}
	 * @param ci     instante de tempo para o qual se deseja as medições
	 * @return {@link TimeUtils#toInt(java.util.Calendar) inteiro que designa o
	 *         instante de tempo} desejado
	 */
	public static Map<String, Meds> readFileH(File folder, int ci) {
		MedH mh = readFileH(ci, folder);

		Map<String, Meds> out = new HashMap<>();

		String[] labels = mh.getLabels();
		byte[] bs = mh.getValues();
		MedDataType[] dataTypes = mh.getDataTypes();
		int bpm = MedDataType.getDataSize(dataTypes);
		int m = bs.length / labels.length / bpm;
		ByteBuffer bb = ByteBuffer.wrap(bs);

		int pos = 0;
		for (int i = 0; i < labels.length; i++) {
			float[] vs = new float[m];
			byte mask = 0;
			byte[] masks = new byte[m - 1];
			for (int j = 0; j < m; j++) {
				for (int k = 0; k < dataTypes.length; k++) {
					MedDataType dt = dataTypes[k];
					switch (dt) {
					case FLOAT:
						vs[j] = bb.getFloat(pos);
						break;
					case INT:
						vs[j] = (float) bb.getInt(pos);
						break;
					case DOUBLE:
						vs[j] = (float) bb.getDouble(pos);
						break;
					case BYTE:
						if (j == 0)
							mask = bs[pos];
						else
							masks[j - 1] = bs[pos];
						break;
					}
					pos += dt.size();
				}
			}
			Meds meds = new Meds(TimeUtils.toCalendar(ci), vs);
			meds.setMask(mask);
			meds.setMasks(masks);
			out.put(labels[i], meds);
		}
		return out;
	}

	private static MedH readFileH(int ci, File folder) {
		if (!folder.exists())
			return null;
		else if (!folder.isDirectory())
			return null;

		File fh = new File(folder.getAbsolutePath() + FOLDER_H);
		if (!fh.exists())
			return null;
		else if (!fh.isDirectory())
			return null;

		// ======================

		// header
		Hheader hh = getHheader(folder);

		// content
		String[] points = hh.getPoints();

		MedH out = new MedH(TimeUtils.toCalendar(ci), points, hh.getDataTypes(), hh.getM());
		try {
			RandomAccessFile raf = new RandomAccessFile(new File(fh.getAbsoluteFile() + "/" + ci + "." + TERM_H), "r");

			byte[] bs = new byte[hh.getM() * hh.getBpm() * hh.getP()];
			raf.read(bs);
			out.setValues(bs);

			raf.close();
			return out;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return out;
	}

	/**
	 * Função que retorna o cabeçalho das medições horizontais
	 * 
	 * @param folder diretório contendo as pastas {@link #FOLDER_H 'h'} e
	 *               {@link #FOLDER_V 'v'}
	 * @return cabeçalho das medições horizontais
	 */
	public static Hheader getHheader(File folder) {
		return (Hheader) getHeader(folder, false);
	}

	// ---------------- auxiliar ----------------

	private static Header getHeader(File folder, boolean v) {
		if (!folder.exists())
			return null;
		else if (!folder.isDirectory())
			return null;

		File fvh = new File(folder.getAbsolutePath() + (v ? FOLDER_V : FOLDER_H));
		if (!fvh.exists())
			return null;
		else if (!fvh.isDirectory())
			return null;

		// ======================

		Header hh = null;
		try {
			// header
			RandomAccessFile raf = new RandomAccessFile(
					new File(fvh.getAbsoluteFile() + "/" + folder.getName() + "." + (v ? TERM_VH : TERM_HH)), "r");
			if (v)
				hh = Vheader.read(raf);
			else
				hh = Hheader.read(raf);
			raf.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return hh;
	}

	public static String[] getAs(Grandeza... anas) {
		String[] out = new String[anas.length];
		for (int i = 0; i < out.length; i++)
			out[i] = String.valueOf(anas[i].ordinal());
		return out;
	}
}
