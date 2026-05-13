package br.com.pereiraeng.measurements.sto;

import java.io.File;
import java.nio.ByteBuffer;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import br.com.pereiraeng.core.Flow;
import br.com.pereiraeng.core.TimeUtils;
import br.com.pereiraeng.core.collections.ArrayUtils;
import br.com.pereiraeng.core.collections.ListUtils;
import br.com.pereiraeng.core.collections.MapUtils;
import br.com.pereiraeng.geo.GeoCoordinate;
import br.com.pereiraeng.geo.objetos.GeoMed;
import br.com.pereiraeng.math.advanced.geometry.Delaunay;
import br.com.pereiraeng.math.geometry.Geom;
import br.com.pereiraeng.math.geometry.Triangle;
import br.com.pereiraeng.math.timeseries.Reg;
import br.com.pereiraeng.math.timeseries.RegP;
import br.com.pereiraeng.math.timeseries.unit.Med;
import br.com.pereiraeng.measurements.MedStatUtils;
import br.com.pereiraeng.measurements.label.MedEtq;
import br.com.pereiraeng.physics.Grandeza;
import br.com.pereiraeng.sql.SQLadapter;
import br.com.pereiraeng.sql.SQLconfig;
import br.com.pereiraeng.sql.Server;

/**
 * <p>
 * <strong>M</strong>easurements <strong>D</strong>ata<strong>B</strong>ase
 * </p>
 * 
 * <p>
 * Esta classe permite a criação e manipulação de tabelas em uma base de dados
 * SQL que guardam medições.
 * </p>
 * 
 * As tabelas que armazenam as medições são:
 * <ul>
 * <li>{@link #MEAS}: tabela principal que associa um número de tag e uma data a
 * um valor;</i>
 * <li>{@link #TAGS}: tabela que associa um número para cada tag;</i>
 * <li>{@link #METADATA}: tabela que associa para cada tag informações extras,
 * como a grandeza medida e o tipo de equipamento monitorado;</i>
 * <li>{@link #SOURCE}: fonte de dados das medições: indica o prefixo que as
 * tags possuem para indicar de onde vem as informações;</i>
 * <li>{@link #DELAUNAY}: tabelas contendo trincas de número que indicam as
 * instalações cujas posições foram agrupadas por triangulação
 * georeferenciada.</i>
 * </ul>
 * 
 * 
 * @author Philipe PEREIRA
 *
 */
public class MDB extends SQLadapter {

	public static final String SEPARADOR = "_";

	public static final int HIS_FILES = 8;

	// ------------------- nomes das tabelas -------------------

	public static final String MEAS = "measurements";

	public static final String TAGS = "tag";

	public static final String METADATA = "tag_data";

	public static final String SOURCE = "source";

	public static final String DELAUNAY = "tag_tri";

	// ------------------- nomes dos campos -------------------

	public static final String LOCAL = "local";

	public static final String TIME = "time";

	public static final String VALUE = "value";

	// sistema circular

	public static final String MONTH = "month", DAY = "day", HOUR = "hour", MINUTE = "minute";

	// sistema tempo+duração

	public static final String DURATION = "duration";

	// sistema data hora

	public static final String DATE = "date";

	// fontes

	public static final String DESC = "description";

	public static final String SEPARATOR_PATTERN = "\\.";

	// ---------------------------------------------------------

	/**
	 * Caminho para o diretório onde se encontram os arquivos .HIS das medições
	 */
	private String folder;

	/**
	 * <ol start="0">
	 * <li>campo do tempo é um {@link Timestamp};</i>
	 * <li>campo do tempo é um {@link TimeUtils#toInt(Calendar) inteiro};</i>
	 * <li>para um sistema circular diário;</i>
	 * <li>para um sistema circular mensal;</i>
	 * <li>para que o campo do tempo seja um {@link TimeUtils#toInt(Calendar)
	 * inteiro} junto com outro campo inteiro que indica por quanto tempo aquele
	 * valor permanece inalterado;</i>
	 * <li>campo do tempo é uma {@link java.sql.Date data} e uma
	 * {@link java.sql.Time hora}.</i>
	 * </ol>
	 */
	protected int timeFields;

	/**
	 * <ol start="0">
	 * <li>não há criação de uma tabela de tags;</i>
	 * <li>há a criação de tabelas para tags e seus grupamentos;</i>
	 * <li>há a criação de tabelas para tags, seus grupamentos e metadados.</i>
	 * </ol>
	 */
	protected int tagsTables;

	/**
	 * Número de medições por linha da tabela de medições
	 */
	protected int measPerRow = 1;

	protected Set<Object> lastMedidoresFound;

	/**
	 * 
	 * @param sql
	 */
	protected MDB(SQLadapter sql) {
		super(sql);
		checkType();
	}

	/**
	 * Construtor da base de dados de medições
	 * 
	 * @param config objeto com as configurações para conexão à base de dados
	 */
	public MDB(SQLconfig config) {
		this(config, null);
	}

	/**
	 * Construtor da base de dados de medições
	 * 
	 * @param config objeto com as configurações para conexão à base de dados
	 * @param folder caminho para o diretório onde se encontram os arquivos .HIS das
	 *               medições
	 */
	public MDB(SQLconfig config, String folder) {
		super(config);
		setFolder(folder);
	}

	/**
	 * Construtor da base de dados de medições
	 * 
	 * @param xmlFilename caminho do arquivo <code>xml</code> que contem as
	 *                    informações de conexão à base de dados
	 */
	public MDB(File xmlFilename) {
		super(xmlFilename);
	}

	@Override
	public boolean connectDB() {
		boolean on = super.connectDB();
		if (on)
			checkType();
		return on || hasHISfiles();
	}

	protected void checkType() {
		try {
			if (conn == null)
				return;

			// forma como o tempo é guardado
			DatabaseMetaData dbmd = conn.getMetaData();
			ResultSet rs = dbmd.getColumns(null, null, getMeasTable(), getTimeField());
			if (rs.next()) { // houver um campo 'time'
				if (rs.getInt("DATA_TYPE") == Types.INTEGER) {
					rs.close();
					rs = dbmd.getColumns(null, null, getMeasTable(), DURATION);
					this.timeFields = rs.next() ? 4 : 1;
				} else
					this.timeFields = 0;
			} else {
				rs.close();
				rs = dbmd.getColumns(null, null, getMeasTable(), MONTH);
				this.timeFields = rs.next() ? 3 : 2;
			}
			rs.close();

			// existência, ou não, da tabela de metadados
			rs = dbmd.getTables(null, null, METADATA, null);
			if (rs.next())
				tagsTables = 2;
			else {
				rs.close();
				rs = dbmd.getTables(null, null, TAGS, null);
				this.tagsTables = rs.next() ? 1 : 0;
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public int getStatus() {
		return super.getStatus() + (hasHISfiles() ? HIS_FILES : 0);
	}

	@Override
	public int getMainConn() {
		int out = getStatus();
		if ((out & ON_LINE) > 0)
			return ON_LINE;
		if ((out & HIS_FILES) > 0)
			return HIS_FILES;
		if ((out & OFF_LINE) > 0)
			return OFF_LINE;
		return NO_ACCESS;
	}

	/**
	 * Função que estabelece o diretório onde se encontram os arquivos históricos
	 * 
	 * @param folder caminho para o diretório onde se encontram os arquivos .HIS das
	 *               medições
	 */
	public void setFolder(String folder) {
		this.folder = folder;
	}

	/**
	 * Função que indica se a base de dados dispõe de dados em arquivos históricos
	 * 
	 * @return <code>true</code> se há arquivos históricos, <code>false</code> se
	 *         não há
	 */
	private boolean hasHISfiles() {
		return folder != null ? new File(folder).exists() : false;
	}

	/**
	 * Função que retorna o nome da tabela que contém as medições
	 * 
	 * @return nome da tabela da base de dados
	 */
	protected String getMeasTable() {
		return MEAS;
	}

	/**
	 * Função que retorna o nome do campo da tabela que guarda o instante de tempo
	 * da medição
	 * 
	 * @return nome do campo da tabela com o instante de tempo
	 */
	protected String getTimeField() {
		return TIME;
	}

	/**
	 * Função que retorna o nome do campo da tabela que guarda o dia da medição
	 * 
	 * @return nome do campo da tabela com o dia da medição
	 */
	protected String getDateField() {
		return DATE;
	}

	protected String getLocalField() {
		return LOCAL;
	}

	protected String[] getValueField(int i) {
		return new String[] { i != 0 ? VALUE + i : VALUE };
	}

	protected int getValuesCount() {
		return this.measPerRow;
	}

	protected int getPos(String quantity) {
		return 0;
	}

	protected String getQuantity(int pos) {
		return null;
	}

	// -------------------------------- GETTERS --------------------------------

	// ----- contínuo -----

	// RegP indexado pelo vetor de tags

	/**
	 * 
	 * @param cs   períodos de tempo a serem baixados (definidos por uma matriz de
	 *             duas colunas, onde os elementos da primeira coluna indicam o
	 *             começo de um subintervalo e os da segunda indicam seu final)
	 * @param freq distância entre as medições, em minutos
	 * @param tags sequência(s) de caracteres que indicam a grandeza medida (com
	 *             prefixo)
	 * @return
	 */
	public RegP get(Calendar[][] cs, int freq, String... tags) {
		final RegP regs = new RegP(tags, freq);
		get(new Flow<Med>() {
			@Override
			public void incomingData(Med data) {
				regs.put(data, data.getChannel());
			}
		}, cs, freq, tags);
		return regs;
	}

	// Flow indexado pelo vetor de tags

	/**
	 * 
	 * @param flow objeto que receberá as medições
	 * @param cs   períodos de tempo a serem baixados (definidos por uma matriz de
	 *             duas colunas, onde os elementos da primeira coluna indicam o
	 *             começo de um subintervalo e os da segunda indicam seu final)
	 * @param tags sequência(s) de caracteres que indicam a grandeza medida (com
	 *             prefixo)
	 */
	public void get(Flow<Med> flow, Calendar[][] cs, int freq, String... tags) {
		int i = getMainConn();
		switch (i) {
		case HIS_FILES:
			for (Calendar[] c : cs)
				HistMed.get(flow, getFiles(c[0], c[1], this.folder), c[0], c[1], false, freq, tags);
			break;
		default:
			String time = getTimeWhere(cs);
			get(flow, time, getRefs(tags));
			break;
		}
	}

	// Flow indexado pelo mapa de tags

	/**
	 * 
	 * @param flow objeto que receberá as medições
	 * @param cs   períodos de tempo a serem baixados
	 * @param tags tabela de dispersão que associa a cada sequência de caracteres
	 *             que indicam a grandeza medida (com prefixo) os canais de envio
	 *             das medições
	 */
	public void get(Flow<Med> flow, Calendar[][] cs, int freq, Map<String, Set<Integer>> tags) {
		int i = getMainConn();
		switch (i) {
		case HIS_FILES:
			for (Calendar[] c : cs)
				HistMed.get(flow, getFiles(c[0], c[1], this.folder), c[0], c[1], freq, tags);
			break;
		default:
			String time = getTimeWhere(cs);
			get(flow, time, getRefs(tags));
			break;
		}
	}

	// ----- discreto -----

	// RegP indexado pelo vetor de tags

	/**
	 * 
	 * @param cs   instantes de tempo a serem baixados
	 * @param tags sequência(s) de caracteres que indicam a grandeza medida (com
	 *             prefixo)
	 * @return
	 */
	public RegP get(Calendar[] cs, String... tags) {
		final RegP regs = new RegP(tags, 60);
		get(new Flow<Med>() {
			@Override
			public void incomingData(Med data) {
				regs.put(data, data.getChannel());
			}
		}, cs, tags);
		return regs;
	}

	// Flow indexado pelo vetor de tags

	/**
	 * 
	 * @param flow objeto que receberá as medições
	 * @param cs   instantes de tempo a serem baixados
	 * @param tags sequência(s) de caracteres que indicam a grandeza medida (com
	 *             prefixo)
	 */
	public void get(Flow<Med> flow, Calendar[] cs, String... tags) {
		int i = getMainConn();
		switch (i) {
		case HIS_FILES:
			HistMed.get(flow, getFiles(cs, this.folder), cs, false, tags);
			break;
		default:
			String time = getTimeWhere(cs);
			get(flow, time, getRefs(tags));
			break;
		}
	}

	// Flow indexado pelo mapa de tags

	/**
	 * 
	 * @param flow objeto que receberá as medições
	 * @param cs   instantes de tempo a serem baixados
	 * @param tags tabela de dispersão que associa a cada sequência de caracteres
	 *             que indicam a grandeza medida (com prefixo) os canais de envio
	 *             das medições
	 */
	public void get(Flow<Med> flow, Calendar[] cs, Map<String, Set<Integer>> tags) {
		int i = getMainConn();
		switch (i) {
		case HIS_FILES:
			HistMed.get(flow, getFiles(cs, this.folder), cs, tags);
			break;
		default:
			String time = getTimeWhere(cs);
			get(flow, time, getRefs(tags));
			break;
		}
	}

	/**
	 * Função que faz uma busca na base de dados do SAS atrás de medições
	 * 
	 * @param flow      objeto que receberá e trabalhará os dados que serão obtidos
	 * @param timeWhere expressão SQL dos períodos de tempo procurados
	 * @param channels  tabela que associa o ponto de medição (tag ou código do
	 *                  medidor) a uma tabela que associa a grandeza a um (ou mais)
	 *                  canal(is)
	 */
	protected void get(Flow<Med> flow, String timeWhere, Map<Object, Map<Integer, Set<Integer>>> channels) {

		// campos

		// - local

		String fields = "`" + getLocalField() + "`";

		// - tempo

		switch (this.timeFields) {
		case 0: // ts
		case 1: // ci
			fields += ", `" + getTimeField() + "`";
			break;
		case 3: // mdhm
			fields += ", `" + MONTH + "`";
		case 2: // dhm
			fields += ", `" + DAY + "`, `" + HOUR + "`, `" + MINUTE + "`";
			break;
		case 4: // ci-du
			fields += ", `" + getTimeField() + "`, `" + DURATION;
			break;
		case 5: // date e time
			fields += ", `" + getDateField() + "`, `" + getTimeField();
			break;
		}

		// - valores

		LinkedHashSet<Integer> grands = new LinkedHashSet<>();
		for (Map<Integer, Set<Integer>> e : channels.values())
			grands.addAll(e.keySet());

		for (Integer g : grands) {
			String[] fieldsNames = getValueField(g);
			for (int i = 0; i < fieldsNames.length; i++)
				fields += ", `" + fieldsNames[i] + "`";
		}

		// -------------------------------------------------------------

		String query = String.format("SELECT %s FROM `%s` WHERE %s AND (%s)", fields, getMeasTable(),
				SQLadapter.getWhere(getLocalField(), channels.keySet(), getType(), false), timeWhere);

		if (MDB.debug)
			System.out.println(query);

		if (lastMedidoresFound != null)
			lastMedidoresFound.clear();

		long start = System.currentTimeMillis();

		int i = getMainConn();
		switch (i) {
		case ON_LINE:
			ResultSet rs = this.query(query);

			if (rs == null)
				return;

			try {
				while (rs.next()) {

					// local

					Object local = rs.getObject(1);
					if (local instanceof String)
						local = ((String) local).trim();

					if (lastMedidoresFound != null)
						lastMedidoresFound.add(local);

					// tempo

					int f = 2;
					Date d = null;
					switch (this.timeFields) {
					case 0: // ts
						d = rs.getTimestamp(f++);
						break;
					case 1: // ci
						d = TimeUtils.toCalendar(rs.getInt(f++)).getTime();
						break;
					case 3: // mdhm
						Calendar c = Calendar.getInstance();
						c.set(Calendar.MONTH, rs.getInt(f++) - 1);
						c.set(Calendar.DAY_OF_MONTH, rs.getInt(f++));
						c.set(Calendar.HOUR_OF_DAY, rs.getInt(f++));
						c.set(Calendar.MINUTE, rs.getInt(f++));
						d = c.getTime();
						break;
					case 2: // dhm
						c = Calendar.getInstance();
						c.set(Calendar.DAY_OF_MONTH, rs.getInt(f++));
						c.set(Calendar.HOUR_OF_DAY, rs.getInt(f++));
						c.set(Calendar.MINUTE, rs.getInt(f++));
						d = c.getTime();
						break;
					case 4: // ci-du
						// TODO
						break;
					case 5: // date time
						Date da = rs.getDate(f++);
						Time t = rs.getTime(f++);
						c = TimeUtils.mergeTimeDate(da, t, true);
						d = c.getTime();
						break;
					}

					// grandezas

					Map<Integer, Set<Integer>> gr2chs = channels.get(local);

					for (Integer grand : grands) {
						// para cada uma das grandezas que foram solicitadas
						Set<Integer> chs = gr2chs.get(grand);
						if (chs != null) { // se para essa tag, essa grandeza foi solicitada
							String[] fs = getValueField(grand);
							float[] values = new float[fs.length];
							for (int j = 0; j < fs.length; j++) {
								float v = rs.getFloat(f++);
								if (rs.wasNull())
									values[j] = Float.NaN;
								else
									values[j] = v;
							}
							float value = validate(grand, values);
							Med m = new Med(d, value);
							for (Integer ch : chs) {
								m.setChannel(ch);
								flow.incomingData(m);
							}
						} else // se para essa tag não se quer essa grandeza...
							f += getValueField(grand).length;
					}
				}
				rs.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			break;
		}

		start = System.currentTimeMillis() - start;
		if (MDB.debug)
			System.out.println("Tempo total da busca na BD: " + start + " ms");
	}

	/**
	 * Função que escolhe entre os valores disponíveis para uma mesma grandeza
	 * 
	 * @param grand  índice da grandeza
	 * @param values valores disponíveis
	 * @return valor escolhido
	 */
	protected float validate(int grand, float... values) {
		return values[0];
	}

	// ============================================================================

	/**
	 * tags -> mapa de chaves
	 * 
	 * @param tags
	 * @return
	 */
	protected Map<Object, Map<Integer, Set<Integer>>> getRefs(String... tags) {
		if (tagsTables == 0) { // tag contem chave local
			Map<Object, Map<Integer, Set<Integer>>> channels = new HashMap<>();
			for (int t = 0; t < tags.length; t++) {
				String[] tagGrand = tags[t].split(SEPARATOR_PATTERN);
				String tag = (String) tagGrand[0];

				Map<Integer, Set<Integer>> gs2ps = channels.get(tag);
				if (gs2ps == null)
					channels.put(tag, gs2ps = new HashMap<>());
				int grand = getPos(tagGrand[1]);
				Set<Integer> ps = gs2ps.get(grand);
				if (ps == null)
					gs2ps.put(grand, ps = new HashSet<>());
				ps.add(t);
			}
			return channels;
		} else { // tabela externa
//			Map<String, Map<Integer, Set<Integer>>> channels = new HashMap<>();
//			for (int t = 0; t < tags.length; t++) {
//				String[] tagGrand = tags[t].split(SEPARATOR_PATTERN);
//
//				Set<Integer> chs = tags2chs.get(tags[t]);
//				if (chs == null)
//					tags2chs.put(tags[t], chs = new HashSet<>());
//				chs.add(t);
//			}
//			return getRefs(tags2chs);
			return null;
		}
	}

	protected Map<Object, Map<Integer, Set<Integer>>> getRefs(Map<String, Set<Integer>> tags2chs) {
		if (tagsTables == 0) { // tag contem chave local
			Map<Object, Map<Integer, Set<Integer>>> channels = new HashMap<>();
			for (Entry<String, Set<Integer>> e : tags2chs.entrySet()) {
				String[] tagGrand = e.getKey().split(SEPARATOR_PATTERN);
				String tag = (String) tagGrand[0];

				Map<Integer, Set<Integer>> gs2ps = channels.get(tag);
				if (gs2ps == null)
					channels.put(tag, gs2ps = new HashMap<>());
				int grand = getPos(tagGrand[1]);
				Set<Integer> ps = gs2ps.get(grand);
				if (ps == null)
					gs2ps.put(grand, ps = new HashSet<>());
				ps.addAll(e.getValue());
			}
			return channels;
		} else { // tabela externa
//			Map<String, Integer> src2ref = MUtils.reverse(getSources(false));
//			String query = "SELECT `ref`, `tag`, `type` FROM `" + TAGS + "` WHERE ";
//			Map<String, Set<Integer>> typeTag2pos = new HashMap<>();
//			for (Entry<String, Set<Integer>> e : tags2ch.entrySet()) {
//				String tt = e.getKey();
//				String tag = tt.substring(4);
//				int type = src2ref.get(tt.substring(0, 3));
//				query += "(`tag`='" + tag + "' AND `type`=" + type + ") OR ";
//				typeTag2pos.put(type + tag, e.getValue());
//			}
//			query = query.substring(0, query.length() - 4);
//
//			Map<Object, Map<Integer, Set<Integer>>> local2chs = new HashMap<>();
//			int i = getMainConn();
//			switch (i) {
//			case ON_LINE:
//				ResultSet rs = this.query(query);
//				try {
//					while (rs.next()) {
//						int local = rs.getInt(1);
//						String tag = rs.getString(2);
//						int type = rs.getInt(3);
//						local2chs.put(local, typeTag2pos.get(type + tag));
//					}
//					rs.close();
//				} catch (SQLException e) {
//					e.printStackTrace();
//				}
//				break;
//			}
			return null;
		}
	}

	protected String getTimeWhere(Calendar[][] cs) {
		String out = "";
		switch (this.timeFields) {
		case 0: // ts
			out = getWhen(getTimeField(), cs);
			break;
		case 1: // ci
			for (Calendar[] c : cs)
				out += String.format("(`%s` >= %d AND `%1$s` < %3$d) OR ", getTimeField(), TimeUtils.toInt(c[0]),
						TimeUtils.toInt(c[1]));
			out = out.substring(0, out.length() - 4);
			break;
		case 2: // dhm
			for (Calendar[] c : cs)
				out += getWhenCirc(c, false) + " OR ";
			out = out.substring(0, out.length() - 4);
			break;
		case 3: // mdhm
			for (Calendar[] c : cs)
				out += getWhenCirc(c, true) + " OR ";
			out = out.substring(0, out.length() - 4);
			break;
		case 4: // ci-du
			for (Calendar[] c : cs)
				out += String.format("(`%s`+`%s` >= %d AND `%1$s` < %4$d) OR ", getTimeField(), DURATION,
						TimeUtils.toInt(c[0]), TimeUtils.toInt(c[1]));
			break;
		case 5: // date e time
			out = getWhen(getDateField(), getTimeField(), cs);
			break;
		}
		return out;
	}

	protected String getTimeWhere(Calendar[] cs) {
		String out = "";
		switch (this.timeFields) {
		case 0: // ts
			out = getWhen(getTimeField(), cs);
			break;
		case 1: // ci
			for (Calendar c : cs)
				out += String.format("(`%s` = %d) OR ", getTimeField(), TimeUtils.toInt(c));
			out = out.substring(0, out.length() - 4);
			break;
		case 2: // dhm
			for (Calendar c : cs)
				out += getWhenCirc(c, false) + " OR ";
			out = out.substring(0, out.length() - 4);
			break;
		case 3: // mdhm
			for (Calendar c : cs)
				out += getWhenCirc(c, true) + " OR ";
			out = out.substring(0, out.length() - 4);
			break;
		case 4: // ci-du
			for (Calendar c : cs)
				out += String.format("(`%s` <= %d AND `%1$s`+`%3$s` >= %2$d) OR ", getTimeField(), TimeUtils.toInt(c),
						DURATION);
			break;
		case 5: // date e time
			out = getWhen(getDateField(), getTimeField(), cs);
			break;
		}
		return out;
	}

	/**
	 * Função usada somente em {@link #timeFields} =2 e {@link #timeFields} =3
	 * 
	 * @param c     vetor com duas posições indicando o instante de tempo inicial e
	 *              final
	 * @param month <code>true</code> para circular mensal, <code>false</code> para
	 *              diário
	 * @return expressão WHERE
	 */
	protected static String getWhenCirc(Calendar[] c, boolean month) {
		String out = "(";
		if (month) {
			out += String.format(
					"(`%s`>%5$tm OR (`%1$s`=%5$tm AND (`%2$s`>%5$te OR (`%2$s`=%5$te AND (`%3$s`>%5$tH OR (`%3$s`=%5$tH AND `%4$s`>=%5$tM))))))",
					MONTH, DAY, HOUR, MINUTE, c[0]);
			out += " AND ";
			out += String.format(
					"(`%s`<%5$tm OR (`%1$s`=%5$tm AND (`%2$s`<%5$te OR (`%2$s`=%5$te AND (`%3$s`<%5$tH OR (`%3$s`=%5$tH AND `%4$s`<%5$tM))))))",
					MONTH, DAY, HOUR, MINUTE, c[1]);
		} else {
			out += String.format(
					"(`%s`>%4$te OR (`%1$s`=%4$te AND (`%2$s`>%4$tH OR (`%2$s`=%4$tH AND `%3$s`>=%4$tM))))", DAY, HOUR,
					MINUTE, c[0]);
			out += " AND ";
			out += String.format("(`%s`<%4$te OR (`%1$s`=%4$te AND (`%2$s`<%4$tH OR (`%2$s`=%4$tH AND `%3$s`<%4$tM))))",
					DAY, HOUR, MINUTE, c[1]);
		}
		return out + ")";
	}

	/**
	 * Função usada somente em {@link #timeFields} =2 e {@link #timeFields} =3
	 * 
	 * @param c     instante de tempo dado
	 * @param month <code>true</code> para circular mensal, <code>false</code> para
	 *              diário
	 * @return expressão WHERE
	 */
	protected static String getWhenCirc(Calendar c, boolean month) {
		if (month)
			return String.format("(`%1$s`=%5$tm AND `%2$s`=%5$te AND `%3$s`=%5$tH AND `%4$s`=%5$tM)", MONTH, DAY, HOUR,
					MINUTE, c);
		else
			return String.format("(`%1$s`=%4$te AND `%2$s`=%4$tH AND `%3$s`=%4$tM)", DAY, HOUR, MINUTE, c);
	}

	// =========================== GEO ===========================

	/**
	 * Discreto, uma coordenada
	 * 
	 * @param cm
	 * @param coord
	 * @param cs
	 * @param triangulation
	 * @return
	 */
	public <G extends GeoMed> RegP get(GeoCoordinate coord, Calendar[] cs, List<List<G>> triangulation) {
		return get(coord, new Calendar[][] { cs }, true, triangulation);
	}

	/**
	 * Contínuo, uma coordenada
	 * 
	 * @param cm
	 * @param coord
	 * @param begin
	 * @param end
	 * @param triangulation
	 * @return
	 */
	public <G extends GeoMed> RegP get(GeoCoordinate coord, Calendar[][] cs, List<List<G>> triangulation) {
		return get(coord, cs, false, triangulation);
	}

	/**
	 * Discreto, mais de uma coordenada
	 * 
	 * @param cm
	 * @param cs
	 * @param triangulation
	 * @param coords
	 * @return
	 */
	public <G extends GeoMed> RegP get(Calendar[] cs, List<List<G>> triangulation, GeoCoordinate... coords) {
		return get(new Calendar[][] { cs }, true, triangulation, coords);
	}

	/**
	 * Contínuo, mais de uma coordenada
	 * 
	 * @param cm
	 * @param begin
	 * @param end
	 * @param triangulation
	 * @param coords
	 * @return
	 */
	public <G extends GeoMed> RegP get(Calendar[][] cs, List<List<G>> triangulation, GeoCoordinate... coords) {
		return get(cs, false, triangulation, coords);
	}

	/**
	 * Uma coordenada
	 * 
	 * @param cm
	 * @param gc
	 * @param coord
	 * @param cs
	 * @param discret
	 * @param triangulation
	 * @return
	 */
	private <G extends GeoMed> RegP get(GeoCoordinate coord, Calendar[][] cs, boolean discret,
			List<List<G>> triangulation) {
		// procurar triângulo onde está inserido o ponto
		GeoMed[] tri = null;
		for (List<G> gvs : triangulation) {
			if (Triangle.hasInside(coord.x, coord.y, gvs.get(0).x, gvs.get(0).y, gvs.get(1).x, gvs.get(1).y,
					gvs.get(2).x, gvs.get(2).y)) {
				tri = gvs.toArray(new GeoMed[3]);
				break;
			}
		}
		RegP out = null;

		if (tri != null) {
			// pegar o valor das temperaturas em cada uma das estações que
			// compõe o triângulo

			// baixar medições
			out = get(cs, discret, tri);

			// reparar medições
			this.restoreGeo(out, tri, cs, discret, getAll(triangulation));

			out.insert(3);
			for (Entry<Integer, float[]> tv : out.entrySet()) {
				float[] vs = tv.getValue();
				vs[3] = (float) Geom.getPlano(coord.x, coord.y, tri[0].x, tri[0].y, vs[0], tri[1].x, tri[1].y, vs[1],
						tri[2].x, tri[2].y, vs[2]);
			}
		} else {
			// as coordenadas apontem para um lugar que não é coberto pela
			// triangularização, pega-se o valor de temperatura da estação mais
			// próxima

			float dist = Float.POSITIVE_INFINITY;
			GeoMed g = null;
			for (List<G> triang : triangulation) {
				for (GeoMed gv : triang) {
					float d = GeoCoordinate.getDistance(coord, gv);
					if (d < dist) {
						dist = d;
						g = gv;
					}
				}
			}
			out = get(cs, discret, new GeoMed[] { g });
		}
		return out.select(out.length() - 1);
	}

	/**
	 * Várias coordenadas
	 * 
	 * @param cm
	 * @param gc
	 * @param cs
	 * @param discret
	 * @param triangulation
	 * @param coords
	 * @return
	 */
	private <G extends GeoMed> RegP get(Calendar[][] cs, boolean discret, List<List<G>> triangulation,
			GeoCoordinate... coords) {
		// 1 procurar os triângulos que contém os pontos solicitados
		Map<Set<GeoMed>, Map<GeoCoordinate, Integer>> tri2coords = new HashMap<>();
		Set<GeoMed> points = new HashSet<>();
		for (int i = 0; i < coords.length; i++) {
			// para cada ponto solicitado...
			for (List<G> triArr : triangulation) {
				if (Triangle.hasInside(coords[i].x, coords[i].y, triArr.get(0).x, triArr.get(0).y, triArr.get(1).x,
						triArr.get(1).y, triArr.get(2).x, triArr.get(2).y)) {
					Set<GeoMed> triSet = new HashSet<>();
					for (G t : triArr)
						triSet.add(t);
					// se este triângulo contiver a coordenada...
					Map<GeoCoordinate, Integer> coords2pos = tri2coords.get(triSet);
					if (coords2pos == null)
						tri2coords.put(triSet, coords2pos = new HashMap<>());
					coords2pos.put(coords[i], i);

					points.addAll(triArr);
					break;
				}
			}
		}

		// 2a baixar tags

		// uma vez que os pontos repetidos (vértices compartilhados dos
		// triângulos) foram removidos (através do Set, equals GeoMed),
		// repassa para o Array
		GeoMed[] estacoes = points.toArray(new GeoMed[points.size()]);
		// baixar valores
		RegP reg = get(cs, discret, estacoes);

		// 2b reparar medições
		this.restoreGeo(reg, estacoes, cs, discret, getAll(triangulation));

		// 3 calcular o valor de cada ponto

		RegP out = new RegP(coords.length, 60);
		for (Entry<Set<GeoMed>, Map<GeoCoordinate, Integer>> e1 : tri2coords.entrySet()) {
			Iterator<GeoMed> tri = e1.getKey().iterator();
			GeoMed tri0 = tri.next();
			GeoMed tri1 = tri.next();
			GeoMed tri2 = tri.next();

			RegP triTemp = reg.select(ArrayUtils.indexOf(estacoes, tri0), ArrayUtils.indexOf(estacoes, tri1),
					ArrayUtils.indexOf(estacoes, tri2));

			Map<GeoCoordinate, Integer> coord2pos = e1.getValue();

			for (Entry<GeoCoordinate, Integer> e2 : coord2pos.entrySet()) {
				// para cada ponto dentro deste triângulo
				GeoCoordinate coord = e2.getKey();
				int pos = e2.getValue();

				for (Entry<Integer, float[]> tv : triTemp.entrySet()) {
					// para cada instante de tempo
					float[] vs = tv.getValue();
					out.put(tv.getKey(), pos, (float) Geom.getPlano(coord.x, coord.y, tri0.x, tri0.y, vs[0], tri1.x,
							tri1.y, vs[1], tri2.x, tri2.y, vs[2]));
				}
			}
		}
		return out;
	}

	/**
	 * 
	 * @param cs       instantes a serem considerados (se for discreto, uma matriz
	 *                 com uma única linha, sendo que esta contém os instantes de
	 *                 tempo solicitados; se contínuo, uma matriz com duas colunas,
	 *                 indicando os trechos iniciais e finais de cada período
	 *                 solicitado)
	 * @param discret  <code>true</code> para datas discretas, <code>false</code>
	 *                 para períodos contínuos de tempo
	 * @param estacoes vetor contendo os objetos georreferenciados que contém as
	 *                 tags relativas às estação
	 * @return registro com as medições
	 */
	private RegP get(Calendar[][] cs, boolean discret, GeoMed... estacoes) {
		String[] tags = new String[estacoes.length];
		for (int i = 0; i < estacoes.length; i++)
			tags[i] = estacoes[i].getTag();
		return discret ? get(cs[0], tags) : get(cs, 60, tags);
	}

	/**
	 * 
	 * @param cm
	 * @param reg           registro de medições
	 * @param gs            vetor com as estações de medição cujos valores foram
	 *                      baixados
	 * @param gc
	 * @param cs            instantes de tempo para os quais as medições foram
	 *                      baixadas
	 * @param discret
	 * @param triangulation triangulação original (contendo todas as estações de
	 *                      medição)
	 */
	private <G extends GeoMed> void restoreGeo(RegP reg, GeoMed[] gs, Calendar[][] cs, boolean discret,
			Set<GeoMed> available) {

		// ================================= 1 =================================
		// primeiramente, ver quais pontos precisam ser reconstruídos

		int end = reg.length();

		// tabela de dispersão que associa para cada conjunto de pontos
		// geográficos que importam e que tem erro uma outra tabela. Esta tabela
		// associa os pontos onde pode haver erro (e.g., nenhum) a uma outra
		// tabela, esta última que associa cada um dos pontos do primeiro
		// conjunto (aqueles que importam e estão errados) os triângulos
		// corretores
		Map<Set<GeoMed>, Map<Set<GeoMed>, Map<GeoMed, GeoMed[]>>> allRescue = new HashMap<>();

		while (true) {
			Map<Set<GeoMed>, Set<Set<GeoMed>>> needRepair = new HashMap<>();
			for (Entry<Integer, float[]> e1 : reg.entrySet()) {
				// varrer todos os registros de temperaturas, para cada instante
				// de tempo e ver quais estão em branco
				Set<GeoMed> nan = new HashSet<>();

				float[] vs = e1.getValue();
				for (int p = 0; p < end; p++)
					if (Float.isNaN(vs[p]))
						nan.add(gs[p]);

				if (nan.size() > 0) {
					// se para um dado instante há registro vazios...
					Map<Set<GeoMed>, Map<GeoMed, GeoMed[]>> tbSemMd2solucao = allRescue.get(nan);

					if (tbSemMd2solucao == null) {
						// acabou-se de descobrir que não tem medição nos pontos
						// -> pede reparo
						Set<Set<GeoMed>> s1 = new HashSet<Set<GeoMed>>();
						s1.add(new HashSet<GeoMed>());
						needRepair.put(nan, s1);
					} else {
						// já se sabia que não tinha medição nos pontos -> ver
						// se as soluções propostas bastam

						Set<GeoMed> tbTemErr = new HashSet<>();

						Map<GeoMed, GeoMed[]> triCorr = null;
						for (Map<GeoMed, GeoMed[]> s1 : tbSemMd2solucao.values()) {
							boolean flag = true;
							for (GeoMed[] tri : s1.values()) {
								for (int t = 0; t < 3; t++) {
									if (Float.isNaN(vs[ArrayUtils.indexOf(gs, tri[t])])) {
										tbTemErr.add(tri[t]);
										flag = false;
									}
								}
							}

							if (flag) {
								triCorr = s1;
								break;
							}
						}

						if (triCorr == null) {
							// se o reparo proposto não é válido (pois há
							// medições erradas nos triângulos reparadores) pede
							// reparo com novas restrições
							Set<Set<GeoMed>> tbNeedRepair = needRepair.get(nan);
							if (tbNeedRepair == null)
								needRepair.put(nan, tbNeedRepair = new HashSet<>());
							tbNeedRepair.add(tbTemErr);
						}
					}
				}
			}

			// se ninguém precisa ser reparado, termina o processo
			if (needRepair.size() == 0)
				break;

			// função que procura os triângulos corretores em função dos pontos
			// que tem erros e dos pontos que não podem ser usados
			MedStatUtils.getRepair(allRescue, needRepair, available);

			// tags que ainda precisam ser baixadas...
			Set<GeoMed> extra = new HashSet<>();
			for (Map<Set<GeoMed>, Map<GeoMed, GeoMed[]>> s0 : allRescue.values()) {
				for (Map<GeoMed, GeoMed[]> s1 : s0.values()) {
					for (GeoMed[] tri : s1.values()) {
						for (int t = 0; t < 3; t++) {
							int index = ArrayUtils.indexOf(gs, tri[t]);
							if (index < 0)
								extra.add(tri[t]);
						}
					}
				}
			}

			if (extra.size() > 0) {
				// se precisa baixar mais alguma temperatura...
				GeoMed[] extraGeo = extra.toArray(new GeoMed[extra.size()]);
				gs = ArrayUtils.concatArray(gs, extraGeo);
				extra.clear();

				RegP.merge(reg, get(cs, discret, extraGeo));
			}
		}

		// ================================= 2 =================================
		// finalmente, recontroi-se os pontos

		for (Entry<Integer, float[]> e : reg.entrySet()) {
			// para cada instante de tempo...
			Set<GeoMed> nan = new HashSet<>();

			float[] vs = e.getValue();
			for (int p = 0; p < end; p++)
				if (Float.isNaN(vs[p]))
					nan.add(gs[p]);

			if (nan.size() > 0) {
				// se para um dado instante há registro vazios...

				Map<Set<GeoMed>, Map<GeoMed, GeoMed[]>> tbSemMd2solucao = allRescue.get(nan);

				Map<GeoMed, GeoMed[]> triCorr = null;
				for (Map<GeoMed, GeoMed[]> s1 : tbSemMd2solucao.values()) {
					boolean flag = true;
					l: for (GeoMed[] tri : s1.values()) {
						for (int t = 0; t < 3; t++) {
							if (Float.isNaN(vs[ArrayUtils.indexOf(gs, tri[t])])) {
								flag = false;
								break l;
							}
						}
					}

					if (flag) {
						triCorr = s1;
						break;
					}
				}

				int instant = e.getKey();
				for (GeoMed nanGeo : nan) {
					// para cada posição vazia...
					int dest = ArrayUtils.indexOf(gs, nanGeo);

					// posições onde estão os valores que vão reparar os erros
					GeoMed[] tri = triCorr.get(gs[dest]);
					int src1 = ArrayUtils.indexOf(gs, tri[0]);
					int src2 = ArrayUtils.indexOf(gs, tri[1]);
					int src3 = ArrayUtils.indexOf(gs, tri[2]);

					float v1 = vs[src1];
					float v2 = vs[src2];
					float v3 = vs[src3];

					float vn = (float) Geom.getPlano(gs[dest].x, gs[dest].y, tri[0].x, tri[0].y, v1, tri[1].x, tri[1].y,
							v2, tri[2].x, tri[2].y, v3);
					reg.put(instant, dest, vn);
				}
			}
		}

		// truncar registros
		reg.setRegs(end);
	}

	private static <G extends GeoMed> Set<GeoMed> getAll(List<List<G>> triangulation) {
		Set<GeoMed> points = new HashSet<>();
		for (List<G> tri : triangulation)
			for (GeoMed g : tri)
				points.add(g);
		return points;
	}

	private static Map<Integer, File> getFiles(Calendar begin, Calendar end, String folder) {
		Map<Integer, File> int2file = new HashMap<>();

		Calendar r = (Calendar) begin.clone();

		// para todos os dias do período
		while (r.before(end)) {
			int2file.put(TimeUtils.date2int(r), new File(String.format(HistMed.FILENAME_FORMAT, folder, 'P', r)));

			// próximo dia
			r.add(Calendar.DAY_OF_MONTH, 1);
			r.set(Calendar.HOUR_OF_DAY, 0);
			r.set(Calendar.MINUTE, 0);
		}

		return int2file;
	}

	private static Map<Integer, File> getFiles(Calendar[] cs, String folder) {
		Map<Integer, File> int2file = new HashMap<>();

		TreeSet<Calendar> days = new TreeSet<>();
		for (int i = 0; i < cs.length; i++)
			days.add(new GregorianCalendar(cs[i].get(Calendar.YEAR), cs[i].get(Calendar.MONTH),
					cs[i].get(Calendar.DAY_OF_MONTH)));

		// para todos os dias do período
		for (Calendar r : days)
			int2file.put(TimeUtils.date2int(r), new File(String.format(HistMed.FILENAME_FORMAT, folder, 'P', r)));

		return int2file;
	}

	// --------------------------------------------------------------

	/**
	 * Função que retorna os limites dos dados de uma dada fonte de dados
	 * 
	 * @param src inteiro que indica a fonte de dados
	 * @return vetor com duas posições com o instante inicial e final entre os quais
	 *         há medições
	 */
	public Calendar[] getLimitsSource(int src) {
		ResultSet rs = query(String.format("SELECT `ref` FROM `%s` WHERE `type`=%d", TAGS, src));
		if (rs != null) {
			// reunir conjunto do número de referências das tags de uma dada
			// fonte
			Set<Integer> set = new HashSet<>();
			try {
				while (rs.next())
					set.add(rs.getInt(1));
				rs.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			return getLimits(set);
		} else if (folder != null) {
			return TimeUtils.getPeriod(folder, "P", ".his");
		} else {
			return null;
		}
	}

	/**
	 * Função que retorna os limites dos dados de um conjunto de tags
	 * 
	 * @param set lista de tags (<code>null</code> para todas as tags)
	 * @return vetor com duas posições com o instante inicial e final entre os quais
	 *         há medições
	 */
	private Calendar[] getLimits(Set<Integer> set) {
		String tf = null;
		switch (this.timeFields) {
		case 0: // ts
		case 1: // ci
			tf = getTimeField();
			break;
		case 3: // mdhm
			tf = MONTH;
		case 2: // dhm
			tf = DAY + "`, `" + HOUR + "`, `" + MINUTE;
			break;
		case 4: // ci-du TODO
			break;
		}

		String query = String.format("SELECT `%s` FROM `%s` WHERE %s", tf, getMeasTable(),
				set != null ? SQLadapter.getWhere(getLocalField(), set, super.getType(), false) : "1");
		if (MDB.debug)
			System.out.println(query);
		long start = System.currentTimeMillis();
		ResultSet rs = query(query);
		Date min = new Date(Long.MAX_VALUE), max = new Date(0L);
		try {
			while (rs.next()) {
				Date d = null;
				switch (this.timeFields) {
				case 0: // ts
					d = rs.getTimestamp(1);
					break;
				case 1: // ci
					d = TimeUtils.toCalendar(rs.getInt(1)).getTime();
					break;
				case 3: // mdhm
					Calendar c = Calendar.getInstance();
					c.set(Calendar.MONTH, rs.getInt(1) - 1);
					c.set(Calendar.DAY_OF_MONTH, rs.getInt(2));
					c.set(Calendar.HOUR_OF_DAY, rs.getInt(3));
					c.set(Calendar.MINUTE, rs.getInt(4));
					d = c.getTime();
					break;
				case 2: // dhm
					c = Calendar.getInstance();
					c.set(Calendar.DAY_OF_MONTH, rs.getInt(1));
					c.set(Calendar.HOUR_OF_DAY, rs.getInt(2));
					c.set(Calendar.MINUTE, rs.getInt(3));
					d = c.getTime();
					break;
				case 4: // ci-du
					// TODO
					break;
				}

				if (d.before(min))
					min = d;
				if (d.after(max))
					max = d;
			}
			rs.close();
			start = System.currentTimeMillis() - start;
			if (MDB.debug)
				System.out.println("Tempo total da busca no MDB: " + start + " ms");
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return new Calendar[] { TimeUtils.date2Calendar(min), TimeUtils.date2Calendar(max) };
	}

	// ---------------------------------- TAGS ---------------------------------

	/**
	 * Função que retorna os grupos de tags existentes no MDB, cada um deles
	 * designado por um trigrama que serve também de prefixo
	 * 
	 * @param full se <code>true</code>, além do trigrama-prefixo da tag, vem também
	 *             uma pequena descrição do grupo de tags
	 * @return tabela que associa a cada inteiro o seu trigrama-prefixo da fonte
	 */
	public Map<Integer, String> getSources(boolean full) {
		Map<Integer, String> out = new HashMap<>();

		String query = "SELECT `ref`, `type`" + (full ? ", `" + DESC + "`" : "") + " FROM `" + SOURCE + "` WHERE 1";

		int i = getMainConn();
		switch (i) {
		case ON_LINE:
			// --------------- on-line ---------------
			try {
				Statement st = (Statement) conn.createStatement();
				ResultSet rs = st.executeQuery(query);

				while (rs.next())
					out.put(rs.getInt(1), rs.getString(2) + (full ? "-" + rs.getString(3) : ""));

				st.close();
				rs.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			break;
		case OFF_LINE:
			// --------------- off-line ---------------

			List<Object[]> q = sqlOffline.executeQuery(query);

			for (Object[] o : q)
				out.put((int) o[0], o[1] + (full ? "-" + o[2] : ""));

			break;
		case HIS_FILES:
			// --------------- hist ---------------

			List<String> tags = HistMed.getTags(this.folder + "\\P160101.his");// TODO último arquivo
			Set<String> srcs = new TreeSet<>();
			for (String s : tags)
				srcs.add(s.substring(0, 3));

			int j = 0;
			for (String s : srcs)
				out.put(j++, s);

			break;
		}

		return out;
	}

	/**
	 * Função que retorna o número de um grupo de tags a partir do prefixo dessas
	 * tags
	 * 
	 * @param prefix trigrama que antecede todas as tags desse grupo
	 * @return número inteiro do grupo de tags
	 */
	public int getSource(String prefix) {
		int out = -1;

		String query = "SELECT `ref` FROM `" + SOURCE + "` WHERE `type`='" + prefix + "'";

		int i = getMainConn();
		switch (i) {
		case ON_LINE:
			// --------------- on-line ---------------
			try {
				Statement st = (Statement) conn.createStatement();
				ResultSet rs = st.executeQuery(query);

				if (rs.next())
					out = rs.getInt(1);

				st.close();
				rs.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			break;
		case OFF_LINE:
			// --------------- off-line ---------------

			List<Object[]> q = sqlOffline.executeQuery(query);

			if (q.size() == 1)
				out = (int) q.get(0)[0];

			break;
		}

		return out;
	}

	/**
	 * Função que retorna o conjunto de tags associadas a um dado grupo
	 * 
	 * @param src inteiro que designa a fonte de dados
	 * @return conjunto de objetos representativos das tags
	 */
	public Set<MedEtq> getTags(int src) {
		Set<MedEtq> out = new HashSet<>();

		String query = String.format("SELECT `ref`, `tag`, `ref_se` FROM `%s` WHERE `type`=%d", TAGS, src);

		int i = getMainConn();
		switch (i) {
		case ON_LINE:
			// --------------- on-line ---------------
			try {
				Statement st = (Statement) conn.createStatement();
				ResultSet rs = st.executeQuery(query);

				while (rs.next())
					out.add(new MedEtq(rs.getInt(1), rs.getString(2), rs.getInt(3)));

				st.close();
				rs.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			break;
		case OFF_LINE: // --------------- off-line ---------------

			List<Object[]> q = sqlOffline.executeQuery(query);

			for (Object[] o : q)
				out.add(new MedEtq((int) o[0], (String) o[1], (int) o[2]));

			break;
		case HIS_FILES: // --------------- hist ---------------

			List<String> tags = HistMed.getTags(this.folder + "\\P160101.his");

			Set<String> srcs = new TreeSet<>();
			for (String s : tags)
				srcs.add(s.substring(0, 3));

			String sr = ListUtils.getElementAt(srcs, src);

			for (String s : tags)
				if (s.startsWith(sr))
					out.add(new MedEtq(s.substring(4)));
			break;
		}

		return out;
	}

	// -------------------------------- WRITE --------------------------------

	/**
	 * <p>
	 * Função que salva na base de dados as medições armazenadas num dado registro.
	 * </p>
	 * 
	 * O objeto que contém as medições deve conter os nomes das tags <strong>com o
	 * prefixo</strong> de três letras que indica a qual grupo tal tag pertence.
	 * 
	 * @param reg registros a serem salvos na base de dados
	 */
	public void write(RegP reg) {
		Map<String, Integer> index = reg.createLabelTable();
		Map<String, Integer> tri2src = MapUtils.reverse(getSources(false));
		Map<String, Map<Integer, Integer>> newIndex = new HashMap<>();

		StringBuilder query = new StringBuilder("SELECT `ref`, `tag`, `type` FROM `" + TAGS + "` WHERE ");
		for (Entry<String, Integer> e : index.entrySet()) {
			String tag = e.getKey();
			int src = tri2src.get(tag.substring(0, 3));
			tag = tag.substring(4);

			query.append(String.format("(`tag`='%s' AND `type`=%d) OR ", tag, src));

			Map<Integer, Integer> type2pos = newIndex.get(tag);
			if (type2pos == null)
				newIndex.put(tag, type2pos = new HashMap<>());
			type2pos.put(src, e.getValue());
		}

		index.clear();
		tri2src.clear();

		// -----------------------------------------

		ResultSet rs = query(query.substring(0, query.length() - 4));

		Map<Integer, Integer> pos2ref = new HashMap<>();
		try {
			while (rs.next())
				pos2ref.put(newIndex.get(rs.getString(2)).get(rs.getInt(3)), rs.getInt(1));
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		write(pos2ref, reg);
	}

	/**
	 * <p>
	 * Função que salva na base de dados as medições armazenadas num dado registro.
	 * </p>
	 * 
	 * O objeto que contém as medições deve conter os nomes das tags <strong>sem o
	 * prefixo</strong> de três letras que indica a qual grupo tal tag pertence
	 * (todas as tags devem pertencer a um <strong>mesmo grupo</strong>, que será
	 * indicado pelo número inteiro que é argumento da função).
	 * 
	 * @param src inteiro que designa a fonte de dados
	 * @param reg registros a serem salvos na base de dados
	 */
	public void write(int src, Reg reg) {
		write(src, reg, false);
	}

	/**
	 * <p>
	 * Função que salva na base de dados as medições armazenadas num dado registro.
	 * </p>
	 * 
	 * O objeto que contém as medições deve conter os nomes das tags <strong>sem o
	 * prefixo</strong> de três letras que indica a qual grupo tal tag pertence
	 * (todas as tags devem pertencer a um <strong>mesmo grupo</strong>, que será
	 * indicado pelo número inteiro que é argumento da função).
	 * 
	 * @param src     inteiro que designa a fonte de dados
	 * @param reg     registros a serem salvos na base de dados
	 * @param newTags
	 */
	public void write(int src, Reg reg, boolean newTags) {
		Map<String, Integer> index = reg.createLabelTable();

		ResultSet rs = query(String.format("SELECT `ref`, `tag` FROM `%s` WHERE %s AND `type`=%d", TAGS,
				getWhere("tag", index.keySet(), super.getType(), false), src));
		Map<Integer, Integer> pos2ref = new HashMap<>();
		try {
			while (rs.next())
				pos2ref.put(index.get(rs.getString(2)), rs.getInt(1));
			rs.close();

			if (newTags) { // se forem tags novas, incluir na tabela `tag`
				Iterator<Entry<String, Integer>> it = index.entrySet().iterator();
				while (it.hasNext()) // remover repetidas
					if (pos2ref.containsKey(it.next().getValue()))
						it.remove();

				int[] refsTag = getVacantKeys("ref", "tag", 0, index.size());
				int i = 0;

				it = index.entrySet().iterator();
				while (it.hasNext()) {
					Entry<String, Integer> e = it.next();
					update(String.format("INSERT INTO `%s`(`ref`, `tag`, `ref_se`, `type`) VALUES (%d,'%s',-1,%d)",
							TAGS, refsTag[i], e.getKey(), src));
					pos2ref.put(e.getValue(), refsTag[i]);
					i++;
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		write(pos2ref, reg);
	}

	private void write(Map<Integer, Integer> pos2ref, Reg reg) {
		String period = null;
		switch (this.timeFields) {
		case 4: // ci-du TODO
		case 0: // ts
			period = getWhen(getTimeField(), new Calendar[][] { reg.getBeginEnd() }, true, true);
			break;
		case 1: // ci
			period = String.format("%s>=%d AND %1$s<=%3$d", getTimeField(), reg.firstKey(), reg.lastKey());
			break;
		case 3: // mdhm TODO
		case 2: // dhm TODO
			break;
		}

		update(String.format("DELETE FROM `measurements`%s AND %s", getWhere("ref_tag", pos2ref.values(), getType()),
				period));

		// -----------------------------------------------------------------------------------------

		String tf = "";
		switch (this.timeFields) {
		case 4: // ci-du
			tf = ", `" + DURATION + "`";
		case 0: // ts
		case 1: // ci
			tf = ", `" + getTimeField() + "`" + tf;
			break;
		case 3: // mdhm
			tf = ", `" + MONTH + "`";
		case 2: // dhm
			tf += String.format(", `%s`, `%s`, `%s`", DAY, HOUR, MINUTE);
			break;
		}

		final String insert = String.format("INSERT INTO `%s`(`%s`, %s%s) VALUES ", getMeasTable(), getLocalField(),
				getValueField(0)[0], tf);
		StringBuilder query = new StringBuilder(insert);

		String format = "(%d,%f,";
		switch (this.timeFields) {
		case 0: // ts
			format += "'%tF %3$tT'";
			break;
		case 1: // ci
			format += "%d";
			break;
		case 2: // dhm
			format += "%te,%3$tH,%3$tM";
			break;
		case 3: // mdhm
			format += "%tm,%3$te,%3$tH,%3$tM";
			break;
		case 4: // ci-du
			format += "%d,%d";
			break;
		}
		format += "),";

		int c = 0;
		if (this.timeFields == 4) {
			Iterator<Entry<Integer, float[]>> it = reg.entrySet().iterator();
			Entry<Integer, float[]> e = it.next();

			float[] values = new float[reg.length()];
			System.arraycopy(e.getValue(), 0, values, 0, values.length);
			int[] cis = ArrayUtils.intVec(e.getKey(), values.length);
			int[] ccis = new int[values.length];

			int[] ds = new int[values.length];
			while (it.hasNext()) {
				e = it.next();
				float[] cvalues = e.getValue();
				for (int v = 0; v < cvalues.length; v++) {

					ccis[v] = e.getKey();
					if (cvalues[v] != values[v]) {
						query.append(
								String.format(Locale.US, format, pos2ref.get(v), values[v], cis[v], ccis[v] - cis[v]));
						c++;
						ds[v] = 0;

						if (c == SQLadapter.MAX_ROW_INSERT) {
							update(query.substring(0, query.length() - 1));
							query = new StringBuilder(insert);
							c = 0;
						}

						cis[v] = ccis[v];
						values[v] = cvalues[v];
					} else
						ds[v]++;
				}
			}
			for (int v = 0; v < ds.length; v++) {
				query.append(String.format(Locale.US, format, pos2ref.get(v), values[v], cis[v], ccis[v] - cis[v]));
				c++;
				if (c == SQLadapter.MAX_ROW_INSERT) {
					update(query.substring(0, query.length() - 1));
					query = new StringBuilder(insert);
					c = 0;
				}
			}
		} else {
			for (Entry<Integer, float[]> e : reg.entrySet()) {
				// time
				Object co;
				if (this.timeFields == 1)
					co = e.getKey();
				else
					co = TimeUtils.toCalendar(e.getKey());

				// values
				float[] vs = e.getValue();
				for (int v = 0; v < vs.length; v++) {
					if (!Float.isNaN(vs[v])) {
						Integer ref = pos2ref.get(v);
						if (ref != null) {
							query.append(String.format(Locale.US, format, ref, vs[v], co));
							c++;
						}

						if (c == MAX_ROW_INSERT) {
							update(query.substring(0, query.length() - 1));
							query = new StringBuilder(insert);
							c = 0;
						}
					}
				}
			}
		}
		if (c > 0)
			update(query.substring(0, query.length() - 1));
	}

	public void write(final int ref, TreeMap<Integer, Float> ci2v) {
		if (ci2v.size() == 0)
			return;
		String tf = "";
		switch (this.timeFields) {
		case 4: // ci-du
			tf = ", `" + DURATION + "`";
		case 0: // ts
		case 1: // ci
			tf = ", `" + getTimeField() + "`" + tf;
			break;
		case 3: // mdhm
			tf = ", `" + MONTH + "`";
		case 2: // dhm
			tf += String.format(", `%s`, `%s`, `%s`", DAY, HOUR, MINUTE);
			break;
		}
		final String insert = String.format("INSERT INTO `%s`(`%s`, `%s`%s) VALUES ", getMeasTable(), getLocalField(),
				getValueField(0)[0], tf);
		StringBuilder query = new StringBuilder(insert);

		String format = "(%d,%f,";
		switch (this.timeFields) {
		case 0: // ts
			format += "'%tF %3$tT'";
			break;
		case 1: // ci
			format += "%d";
			break;
		case 2: // dhm
			format += "%te,%3$tH,%3$tM";
			break;
		case 3: // mdhm
			format += "%tm,%3$te,%3$tH,%3$tM";
			break;
		case 4: // ci-du
			format += "%d,%d";
			break;
		}
		format += "),";

		Iterator<Entry<Integer, Float>> it = ci2v.entrySet().iterator();
		int c = 0;
		if (this.timeFields == 4) {
			Entry<Integer, Float> e = it.next();

			int ci = e.getKey();
			int cci = ci;
			float value = e.getValue();

			int d = 0;
			while (it.hasNext()) {
				e = it.next();
				cci = e.getKey();
				float cvalue = e.getValue();
				if (cvalue != value) {
					query.append(String.format(Locale.US, format, ref, value, ci, cci - ci));
					c++;
					d = 0;

					if (c == SQLadapter.MAX_ROW_INSERT) {
						update(query.substring(0, query.length() - 1));
						query = new StringBuilder(insert);
						c = 0;
					}

					ci = cci;
					value = cvalue;
				} else
					d++;
			}
			if (d > 0) {
				query.append(String.format(Locale.US, format, ref, value, ci, cci - ci));
				c++;
			}
		} else {
			while (it.hasNext()) {
				Entry<Integer, Float> e = it.next();

				// time
				Object co;
				if (this.timeFields == 1)
					co = e.getKey();
				else
					co = TimeUtils.toCalendar(e.getKey());

				query.append(String.format(Locale.US, format, ref, e.getValue(), co));
				c++;
				if (c == SQLadapter.MAX_ROW_INSERT) {
					update(query.substring(0, query.length() - 1));
					query = new StringBuilder(insert);
					c = 0;
				}
			}
		}
		if (c > 0)
			update(query.substring(0, query.length() - 1));
	}

	/**
	 * Função que checa a continuidade dos registros de uma dada base de dados de
	 * medidas do tipo {@link #timeFields 4}. Esta função deve ser chamada após se
	 * adicionar registros a uma dada base de modo a verificar se não há situações
	 * onde o valor permanece constante, porém, devido a uma quebra da base de dados
	 * de origem
	 * 
	 * @param c    instante de tempo em que há uma quebra
	 * @param step tempo, em segundo, em que as grandezas são amostradas
	 */
	public void checkContinuity(Calendar c, int step) {
		if (this.timeFields != 4)
			throw new IllegalArgumentException("Só faz sentido checar a continuidade de uma base do tipo 4");

		Map<Integer, byte[]> pv = new HashMap<>(), nv = new HashMap<>();
		int ci = TimeUtils.toInt(c);
		ResultSet rs = query(String.format(
				"SELECT `ref_tag`, `value`, `time`, `duration` FROM `%s` WHERE (`time`+`duration`=%d OR `time`=%d)",
				getMeasTable(), ci - step, ci));
		try {
			while (rs.next()) {
				int refTag = rs.getInt(1);
				float value = rs.getFloat(2);
				int time = rs.getInt(3);
				int duration = rs.getInt(4);

				boolean bn = time == ci;
				byte[] o = (bn ? pv : nv).get(refTag);

				if (o == null) {// espera o outro vir
					byte[] bs = new byte[12];
					ByteBuffer.wrap(bs).putFloat(value);
					ByteBuffer.wrap(bs, 4, 4).putInt(time);
					ByteBuffer.wrap(bs, 8, 4).putInt(duration);
					(bn ? nv : pv).put(refTag, bs);
				} else {
					float ov = ByteBuffer.wrap(o, 0, 4).getFloat();
					if (ov == value) {
						int ot = ByteBuffer.wrap(o, 4, 4).getInt();
						int od = ByteBuffer.wrap(o, 8, 4).getInt();
						this.update(String.format(
								"UPDATE `measurements` SET `duration`=%d WHERE `ref_tag`=%d AND `time`=%d",
								duration + od + step, refTag, bn ? ot : time));
						this.update(String.format("DELETE FROM `measurements` WHERE `ref_tag`=%d AND `time`=%d", refTag,
								bn ? time : ot));
					}
				}
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	// -------------------------------- DELETE --------------------------------

	/**
	 * Função que apaga todos os registro para uma dada fonte
	 * 
	 * @param tags <code>true</code> para apagar apagar também as tags,
	 *             <code>false</code> para apagar só os registros
	 * @param src  inteiro que designa a fonte de dados
	 */
	public void delete(boolean tags, int src) {
		ResultSet rs = query(String.format("SELECT `ref` FROM `%s` WHERE `type`=%d", TAGS, src));
		try {
			while (rs.next()) {
				update(String.format("DELETE FROM `%s` WHERE `%s`=%d", getMeasTable(), getLocalField(), rs.getInt(1)));
				if (tags && this.tagsTables == 2) // tag_data
					update(String.format("DELETE FROM `%s` WHERE `%s`=%d", METADATA, getLocalField(), rs.getInt(1)));
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		if (tags && this.tagsTables > 0) {
			update(String.format("DELETE FROM `%s` WHERE `type`=%d", TAGS, src)); // tag
			update("DELETE FROM `" + SOURCE + "` WHERE `ref`=" + src); // source
		}
	}

	// ------------------------ MOVE DATA ------------------------

	/**
	 * Função que transfere os dados de medição entre {@link MDB bases de dados}
	 * diferentes
	 * 
	 * @param srcDB   nome da base de dados de onde partirão os dados
	 * @param desDB   nome da base de dados para onde irão os dados
	 * @param common  <code>true</code> para copiar o conteúdo das tabelas que não
	 *                dependem da fonte de dados
	 * @param sources inteiros que indicam as categorias de tags e dados que serão
	 *                transferidos
	 */
	public static void transferData(String srcDB, String desDB, boolean common, int... sources) {
		// fonte
		MDB src = new MDB(new SQLconfig(srcDB));
		src.config();
		src.connectDB();

		// destino
		MDB dest = new MDB(new SQLconfig(desDB));
		dest.config();
		dest.connectDB();

		transferData(src, dest, common, sources);

		src.disconnectDB();

		dest.disconnectDB();
	}

	/**
	 * Função que transfere os dados de medição entre {@link MDB bases de dados}
	 * diferentes
	 * 
	 * @param src     base de dados de onde partirão os dados
	 * @param dest    base de dados para onde irão os dados
	 * @param common  <code>true</code> para copiar o conteúdo das tabelas que não
	 *                dependem da fonte de dados (e.g., a tabela de triângulos
	 *                geográficos)
	 * @param sources inteiros que indicam as categorias de tags e dados que serão
	 *                transferidos (todas as tags se nenhuma categoria for
	 *                selecionada)
	 */
	public static void transferData(MDB src, MDB dest, boolean common, int... sources) {
		// transferir
		try {
			// --------- source ---------
			ResultSet rs = src.query(String.format("SELECT `ref`, `type`, `%s` FROM `%s` WHERE %s", DESC, SOURCE,
					sources.length == 0 ? "1" : getWhere("ref", ArrayUtils.box(sources), src.getType(), false)));
			while (rs.next())
				dest.update(String.format("INSERT INTO `%s`(`ref`, `type`, `%s`) VALUES (%d,'%s','%s')", SOURCE, DESC,
						rs.getInt(1), rs.getString(2), rs.getString(3)));
			rs.close();

			// --------- tag ---------
			Set<Integer> refTags = new HashSet<>();
			rs = src.query(String.format("SELECT `ref`, `tag`, `ref_se`, `type` FROM `%s` WHERE %s", TAGS,
					sources.length == 0 ? "1" : getWhere("type", ArrayUtils.box(sources), src.getType(), false)));
			while (rs.next()) {
				int ref = rs.getInt(1);
				refTags.add(ref);
				dest.update(String.format("INSERT INTO `%s`(`ref`, `tag`, `ref_se`, `type`) VALUES (%d,'%s',%d,%d)",
						TAGS, ref, rs.getString(2), rs.getInt(3), rs.getInt(4)));
			}
			rs.close();

			String where = getWhere(src.getLocalField(), refTags, src.getType());

			// --------- tag_data ---------
			rs = src.query(String.format("SELECT `%s`, `equip`, `ref_eqs`, `grandeza`, `info` FROM `%s`%s",
					src.getLocalField(), METADATA, where));
			while (rs.next())
				dest.update(String.format(
						"INSERT INTO `%s`(`%s`, `equip`, `ref_eqs`, `grandeza`, `info`) VALUES (%d,'%s',%d,'%s','%s')",
						METADATA, dest.getLocalField(), rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getString(4),
						rs.getString(5)));
			rs.close();

			if (common) {
				// carregar a tabela que é comum a todas as grandezas: a tabela
				// de triângulos
				// geográficos

				// --------- tag_tri ---------
				rs = src.query("SELECT `ref_geo1`, `ref_geo2`, `ref_geo3` FROM `" + DELAUNAY + "` WHERE 1");
				while (rs.next())
					dest.update(String.format("INSERT INTO `%s`(`ref_geo1`, `ref_geo2`, `ref_geo3`) VALUES (%d,%d,%d)",
							DELAUNAY, rs.getInt(1), rs.getInt(2), rs.getInt(3)));
				rs.close();
			}

			// --------- data ---------

			// time fields
			String tf = "";
			switch (src.timeFields) {
			case 4: // ci-du
				tf = ", `" + DURATION + "`";
			case 0: // ts
			case 1: // ci
				tf = ", `" + TIME + "`" + tf;
				break;
			case 3: // mdhm
				tf = ", `" + MONTH + "`";
			case 2: // dhm
				tf += String.format(", `%s`, `%s`, `%s`", DAY, HOUR, MINUTE);
				break;
			}

			rs = src.query(String.format("SELECT `%s`, `%s`%s FROM `%s`%s", src.getLocalField(),
					src.getValueField(0)[0], tf, src.getMeasTable(), where));

			tf = "";
			switch (dest.timeFields) {
			case 4: // ci-du
				tf = ", `" + DURATION + "`";
			case 0: // ts
			case 1: // ci
				tf = ", `" + dest.getTimeField() + "`" + tf;
				break;
			case 3: // mdhm
				tf = ", `" + MONTH + "`";
			case 2: // dhm
				tf += String.format(", `%s`, `%s`, `%s`", DAY, HOUR, MINUTE);
				break;
			}

			final String insert = String.format("INSERT INTO `%s`(`%s`, `%s`%s) VALUES ", dest.getMeasTable(),
					dest.getLocalField(), dest.getValueField(0)[0], tf);
			StringBuilder query = new StringBuilder(insert);
			int c = 0;
			while (rs.next()) {
				String time = null;

				switch (src.timeFields) {
				case 0: // ts
					Timestamp ts = rs.getTimestamp(3);
					switch (dest.timeFields) {
					case 0: // ts
						time = String.format("'%1$tF %1$tT'", ts);
						break;
					case 1: // ci
						time = String.valueOf(TimeUtils.toInt(ts));
						break;
					case 2: // dhm
						time = String.format("%1$te,%1$tH,%1$tM", ts);
						break;
					case 3: // mdhm
						time = String.format("%1$tm,%1$te,%1$tH,%1$tM", ts);
						break;
					case 4: // ci-du
						// TODO
						break;
					}
					break;
				case 1: // ci
					int ti = rs.getInt(3);
					switch (dest.timeFields) {
					case 0: // ts
						time = String.format("'%1$tF %1$tT'", TimeUtils.toCalendar(ti));
						break;
					case 1: // ci
						time = String.valueOf(ti);
						break;
					case 2: // dhm
						time = String.format("%1$te,%1$tH,%1$tM", TimeUtils.toCalendar(ti));
						break;
					case 3: // mdhm
						time = String.format("%1$tm,%1$te,%1$tH,%1$tM", TimeUtils.toCalendar(ti));
						break;
					case 4: // ci-du
						// TODO
						break;
					}
					break;
				case 2: // dhm
					int day = rs.getInt(3);
					int hrs = rs.getInt(4);
					int min = rs.getInt(5);
					switch (dest.timeFields) {
					case 0: // ts
						Calendar d = Calendar.getInstance();
						d.set(Calendar.DAY_OF_MONTH, day);
						d.set(Calendar.HOUR_OF_DAY, hrs);
						d.set(Calendar.MINUTE, min);
						time = String.format("'%1$tF %1$tT'", d);
						break;
					case 1: // ci
						d = Calendar.getInstance();
						d.set(Calendar.DAY_OF_MONTH, day);
						d.set(Calendar.HOUR_OF_DAY, hrs);
						d.set(Calendar.MINUTE, min);
						time = String.valueOf(TimeUtils.toInt(d));
						break;
					case 2: // dhm
						time = String.format("%d,%d,%d", day, hrs, min);
						break;
					case 3: // mdhm
						time = String.format("%tm,%d,%d,%d", Calendar.getInstance(), day, hrs, min);
						break;
					case 4: // ci-du
						// TODO
						break;
					}
					break;
				case 3: // mdhm
					int mth = rs.getInt(3);
					day = rs.getInt(4);
					hrs = rs.getInt(5);
					min = rs.getInt(6);
					switch (dest.timeFields) {
					case 0: // ts
						Calendar d = Calendar.getInstance();
						d.set(Calendar.MONTH, mth - 1);
						d.set(Calendar.DAY_OF_MONTH, day);
						d.set(Calendar.HOUR_OF_DAY, hrs);
						d.set(Calendar.MINUTE, min);
						time = String.format("'%1$tF %1$tT'", d);
						break;
					case 1: // ci
						d = Calendar.getInstance();
						d.set(Calendar.MONTH, mth - 1);
						d.set(Calendar.DAY_OF_MONTH, day);
						d.set(Calendar.HOUR_OF_DAY, hrs);
						d.set(Calendar.MINUTE, min);
						time = String.valueOf(TimeUtils.toInt(d));
						break;
					case 2: // dhm
						time = String.format("%d,%d,%d", day, hrs, min);
						break;
					case 3: // mdhm
						time = String.format("%d,%d,%d,%d", mth, day, hrs, min);
						break;
					case 4: // ci-du
						// TODO
						break;
					}
					break;
				case 4: // ci-du
					// TODO
					switch (dest.timeFields) {
					case 0: // ts
						// TODO
						break;
					case 1: // ci
						// TODO
						break;
					case 2: // dhm
						// TODO
						break;
					case 3: // mdhm
						// TODO
						break;
					case 4: // ci-du
						// TODO
						break;
					}
					break;
				}

				query.append(String.format(Locale.US, "(%d,%f,%s),", rs.getInt(1), rs.getFloat(2), time));
				c++;
				if (c == MAX_ROW_INSERT) {
					dest.update(query.substring(0, query.length() - 1));
					query = new StringBuilder(insert);
					c = 0;
				}
			}
			rs.close();
			if (c > 0)
				dest.update(query.substring(0, query.length() - 1));

		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void generateHISfiles(String folder, Calendar[] cs) {
		// reunir tags

		List<String> tags = new LinkedList<>();
		Map<Integer, String> srcs = getSources(false);
		for (Entry<Integer, String> e : srcs.entrySet()) {
			Set<MedEtq> ts = getTags(e.getKey());
			for (MedEtq t : ts)
				tags.add(e.getValue() + SEPARADOR + t.getEtq());
		}

		if (cs == null)
			cs = getLimits(null);

		if (MDB.debug)
			System.out.printf("Há medições entre %tF\t%tF\n", cs[0], cs[1]);

		Calendar b = (Calendar) cs[0].clone();
		b.set(Calendar.HOUR_OF_DAY, 0);
		b.set(Calendar.MINUTE, 0);
		b.set(Calendar.SECOND, 0);
		while (true) {
			if (MDB.debug)
				System.out.printf("Baixando %tF\n", b);

			// fim do dia
			Calendar e = (Calendar) b.clone();
			e.add(Calendar.DAY_OF_MONTH, 1);
			e.add(Calendar.MINUTE, -1);

			// medições de somente um dia
			RegP reg = get(new Calendar[][] { { b, e } }, 60, tags.toArray(new String[tags.size()]));
			if (reg.size() > 0) {
				reg.removeEmpty();
				HistMed.writeFile(folder, reg);
			}
			reg.clear();

			// próximo dia
			b.add(Calendar.DAY_OF_MONTH, 1);
			if (b.after(cs[1]))
				break;
		}
	}

	// ------------------------ GEORREFERENCIADO ------------------------

	/**
	 * Função que retorna os triângulos obtidos a partir da triangularização de
	 * Delaunay das estações de medição.
	 * 
	 * @param gs conjunto de pontos geográficos representando a localização dos
	 *           medidores, identificados por um {@link GeoMed#getId() número
	 *           inteiro}
	 * 
	 * @return lista de trincas de objetos das estações de medição, representando os
	 *         vértices dos triângulos
	 */
	public <F extends GeoMed> List<List<F>> getTriangulation(Collection<? extends F> gs) {
		// criar tabela que associa a referência (id do placemark - chave
		// primária) ao objeto que representa a estação de medição
		HashMap<Integer, F> ref2est = new HashMap<>();

		for (F g : gs)
			ref2est.put((int) g.getId(), g);

		// conjunto de triângulos que foram previamente selecionados
		List<List<F>> tri = new LinkedList<>();

		String query = "SELECT * FROM `" + DELAUNAY + "` WHERE 1";
		int i = getMainConn();
		switch (i) {
		case ON_LINE:
			// --------------- on-line ---------------
			try {
				Statement st = (Statement) conn.createStatement();
				ResultSet rs = st.executeQuery(query);

				while (rs.next()) {
					F gv1 = ref2est.get(rs.getInt(1));
					F gv2 = ref2est.get(rs.getInt(2));
					F gv3 = ref2est.get(rs.getInt(3));
					if (gv1 != null && gv2 != null && gv3 != null)
						tri.add(Arrays.asList(gv1, gv2, gv3));
				}
				rs.close();
				st.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			break;
		case OFF_LINE:
			// --------------- off-line ---------------

			List<Object[]> objs = sqlOffline.executeQuery(query);
			for (Object[] obj : objs) {
				F gv1 = ref2est.get((int) obj[0]);
				F gv2 = ref2est.get((int) obj[1]);
				F gv3 = ref2est.get((int) obj[2]);
				if (gv1 != null && gv2 != null && gv3 != null)
					tri.add(Arrays.asList(gv1, gv2, gv3));
			}
			break;
		default:
			break;
		}
		return tri;
	}

	public void updateTriangles(Set<? extends GeoMed> gs) {
		Collection<List<GeoMed>> tris = Delaunay.delaunayTriangulationF(gs);
		update("DELETE FROM `" + DELAUNAY + "` WHERE 1");
		for (List<GeoMed> t : tris)
			update(String.format("INSERT INTO `%s`(`ref_geo1`, `ref_geo2`, `ref_geo3`) VALUES (%d, %d, %d)", DELAUNAY,
					t.get(0).getId(), t.get(1).getId(), t.get(2).getId()));
	}

	// ------------------------ CREATE DB ------------------------

	/**
	 * Função que cria uma nova base de dados com as tabelas do sistema de
	 * armazenamento de medições MDB no {@link Server#MySQL servidor MySQL} local
	 * 
	 * @param db         nome da base de dados a ser criada
	 * @param createDB   <code>true</code> para criar uma nova base de dados,
	 *                   <code>false</code> caso a base de dados já esteja criada
	 *                   (neste caso, somente as tabelas que serão criadas)
	 * @param tagTables
	 *                   <ol start="0">
	 *                   <li>não há criação de uma tabela de tags;</i>
	 *                   <li>há a criação de tabelas para tags e seus
	 *                   grupamentos;</i>
	 *                   <li>há a criação de tabelas para tags, seus grupamentos e
	 *                   metadados.</i>
	 *                   </ol>
	 * @param geoTable   se <code>true</code> há o suporte para grandezas
	 *                   georreferenciadas, <code>false</code> se não
	 * @param timeFields
	 *                   <ol start="0">
	 *                   <li>campo do tempo é um {@link Timestamp};</i>
	 *                   <li>para que o campo do tempo seja um
	 *                   {@link TimeUtils#toInt(Calendar) inteiro};</i>
	 *                   <li>para um sistema circular diário;</i>
	 *                   <li>para um sistema circular mensal;</i>
	 *                   <li>para que o campo do tempo seja um
	 *                   {@link TimeUtils#toInt(Calendar) inteiro} junto com outro
	 *                   campo inteiro que indica por quanto tempo aquele valor
	 *                   permanece inalterado.</i>
	 *                   </ol>
	 * @param values     número de valores medidos por entrada
	 */
	public static void createMDB(String db, boolean createDB, int tagTables, boolean geoTable, int timeFields,
			int values) {
		createMDB(new SQLconfig(db), createDB, tagTables, geoTable, timeFields, values);
	}

	/**
	 * Função que cria uma nova base de dados com as tabelas do sistema de
	 * armazenamento de medições MDB no sistema de arquivo (ou seja,
	 * {@link Server#SQLite sem servidor})
	 * 
	 * @param folder     diretório com o arquivo
	 * @param file       nome do arquivo (sem a terminação .sql)
	 * @param tagTables
	 *                   <ol start="0">
	 *                   <li>não há criação de uma tabela de tags;</i>
	 *                   <li>há a criação de tabelas para tags e seus
	 *                   grupamentos;</i>
	 *                   <li>há a criação de tabelas para tags, seus grupamentos e
	 *                   metadados.</i>
	 *                   </ol>
	 * @param geoTable   se <code>true</code> há o suporte para grandezas
	 *                   georreferenciadas, <code>false</code> se não
	 * @param timeFields
	 *                   <ol>
	 *                   <li>para que o campo do tempo seja um
	 *                   {@link TimeUtils#toInt(Calendar) inteiro};</i>
	 *                   <li>para um sistema circular diário;</i>
	 *                   <li>para um sistema circular mensal;</i>
	 *                   <li>para que o campo do tempo seja um
	 *                   {@link TimeUtils#toInt(Calendar) inteiro} junto com outro
	 *                   campo inteiro que indica por quanto tempo aquele valor
	 *                   permanece inalterado.</i>
	 *                   </ol>
	 * @param values     número de valores medidos por entrada
	 */
	public static void createMDB(File folder, String file, int tagTables, boolean geoTable, int timeFields,
			int values) {
		if (timeFields == 0)
			throw new IllegalArgumentException("SQLite não trabalha bem com Timestamp.");
		createMDB(new SQLconfig(folder, file), false, tagTables, geoTable, timeFields, values);
	}

	private static void createMDB(SQLconfig config, boolean createDB, int tagTables, boolean geoTable, int timeFields,
			int values) {
		createMDB(config.getType(), config.getServer(), config.getPort(), config.getLogin(), config.getPassword(),
				config.getDb(), createDB, tagTables, geoTable, timeFields, values);
	}

	/**
	 * Função que cria uma nova base de dados com as tabelas do sistema de
	 * armazenamento de medições MDB
	 * 
	 * @param type       tipo de base de dados (MySQL, SQLserver, SAS ou SQLite)
	 * @param server     servidor
	 * @param port       porta
	 * @param login      usuário
	 * @param password   senha
	 * @param db         nome da base de dados a ser criada
	 * @param createDB   <code>true</code> para criar uma nova base de dados,
	 *                   <code>false</code> caso a base de dados já esteja criada
	 *                   (neste caso, somente as tabelas que serão criadas)
	 * @param tagTables
	 *                   <ol start="0">
	 *                   <li>não há criação de uma tabela de tags;</i>
	 *                   <li>há a criação de tabelas para tags e seus
	 *                   grupamentos;</i>
	 *                   <li>há a criação de tabelas para tags, seus grupamentos e
	 *                   metadados.</i>
	 *                   </ol>
	 * @param geoTable   se <code>true</code> há o suporte para grandezas
	 *                   georreferenciadas, <code>false</code> se não
	 * @param timeFields
	 *                   <ol start="0">
	 *                   <li>campo do tempo é um {@link Timestamp};</i>
	 *                   <li>para que o campo do tempo seja um
	 *                   {@link TimeUtils#toInt(Calendar) inteiro};</i>
	 *                   <li>para um sistema circular diário;</i>
	 *                   <li>para um sistema circular mensal;</i>
	 *                   <li>para que o campo do tempo seja um
	 *                   {@link TimeUtils#toInt(Calendar) inteiro} junto com outro
	 *                   campo inteiro que indica por quanto tempo aquele valor
	 *                   permanece inalterado.</i>
	 *                   </ol>
	 * @param values     número de valores medidos por entrada
	 */
	public static void createMDB(Server type, String server, String port, String login, String password, String db,
			boolean createDB, int tagTables, boolean geoTable, int timeFields, int values) {
		if (createDB)
			SQLadapter.createBD(type, server, port, login, password, db);

		MDB mdb = new MDB(new SQLconfig(type, server, port, login, password, db));
		mdb.config();
		mdb.connectDB();

		// criar tabelas

		// tabela de medições
		StringBuilder s = new StringBuilder(String.format("CREATE TABLE `%s`(`%s` %s", mdb.getMeasTable(),
				mdb.getLocalField(), SQLadapter.getTypeName(type, SQLadapter.MEDIUMINT, false, 0, 0, false)));

		// identificação do instante de tempo
		switch (timeFields) {
		case 0:
		case 1:
		case 4:
			s.append(String.format(", `%s` %s", mdb.getTimeField(), SQLadapter.getTypeName(type,
					timeFields == 0 ? Types.TIMESTAMP : Types.INTEGER, false, 0, 0, false)));
			if (timeFields == 4)
				s.append(String.format(", `%s` %s", DURATION,
						SQLadapter.getTypeName(type, Types.INTEGER, false, 0, 0, false)));
			break;
		case 3:
			s.append(
					String.format(", `%s` %s", MONTH, SQLadapter.getTypeName(type, Types.TINYINT, false, 0, 0, false)));
		case 2:
			s.append(String.format(", `%s` %s, `%s` %s, `%s` %s", DAY,
					SQLadapter.getTypeName(type, Types.TINYINT, false, 0, 0, false), HOUR,
					SQLadapter.getTypeName(type, Types.TINYINT, false, 0, 0, false), MINUTE,
					SQLadapter.getTypeName(type, Types.TINYINT, false, 0, 0, false)));
			break;
		}

		// valores
		if (values == 1)
			s.append(String.format(", `%s` %s", mdb.getValueField(0)[0],
					SQLadapter.getTypeName(type, Types.FLOAT, false, 0, 0, false)));
		else
			for (int i = 1; i <= values; i++)
				s.append(String.format(", `%s%d` %s", mdb.getValueField(0)[0], i,
						SQLadapter.getTypeName(type, Types.FLOAT, false, 0, 0, false)));

		// chaves primárias
		s.append(", PRIMARY KEY ( `" + mdb.getLocalField() + "`, ");
		if (timeFields == 0 || timeFields == 1 || timeFields == 4)
			s.append(String.format("`%s`", mdb.getTimeField()));
		else if (timeFields == 2 || timeFields == 3) {
			s.append(String.format("`%s`, `%s`", HOUR, MINUTE));
			if (timeFields == 3)
				s.append(String.format(", `%s` ", DAY));
		}
		s.append("))");

		mdb.update(s.toString());

		// tabela de tags
		switch (tagTables) {
		case 2:
			mdb.update(String.format(
					"CREATE TABLE `%s`(`%s` %s, `equip` %s, `ref_eqs` %s, `grandeza` %s, `info` %s, PRIMARY KEY ( `%2$s` ))",
					METADATA, mdb.getLocalField(),
					SQLadapter.getTypeName(type, SQLadapter.MEDIUMINT, false, 0, 0, false),
					type == Server.MySQL ? "ENUM('SE', 'EM')"
							: SQLadapter.getTypeName(type, Types.CHAR, false, 2, 0, false),
					SQLadapter.getTypeName(type, Types.INTEGER, false, 0, 0, false),
					type == Server.MySQL ? "ENUM('POTA','TEMP','IRAD','PRES','MM','ANG','VELO','PORC')"
							: SQLadapter.getTypeName(type, Types.CHAR, false, 4, 0, false),
					SQLadapter.getTypeName(type, Types.CHAR, false, 1, 0, false)));
		case 1:
			mdb.update(String.format("CREATE TABLE `%s`(`ref` %s, `type` %s, `%s` %s, PRIMARY KEY ( `ref` ))", SOURCE,
					SQLadapter.getTypeName(type, Types.TINYINT, true, 0, 0, false),
					SQLadapter.getTypeName(type, Types.CHAR, false, 3, 0, false), DESC,
					SQLadapter.getTypeName(type, Types.VARCHAR, false, 256, 0, false)));
			mdb.update(String.format(String.format(
					"CREATE TABLE `%s`(`ref` %s, `tag` %s, `ref_se` %s, `type` %s, PRIMARY KEY ( `ref` ))", TAGS,
					SQLadapter.getTypeName(type, SQLadapter.MEDIUMINT, true, 0, 0, false),
					SQLadapter.getTypeName(type, Types.CHAR, false, 30, 0, false),
					SQLadapter.getTypeName(type, Types.INTEGER, false, 0, 0, false),
					SQLadapter.getTypeName(type, Types.TINYINT, false, 0, 0, false))));
			break;
		}

		// tabela de georreferenciamento
		if (geoTable)
			mdb.update(String.format(
					"CREATE TABLE `%s`(`ref_geo1` %s, `ref_geo2` %s, `ref_geo3` %s, PRIMARY KEY ( `ref_geo1`, `ref_geo2`, `ref_geo3` ))",
					DELAUNAY, SQLadapter.getTypeName(type, SQLadapter.MEDIUMINT, true, 0, 0, false),
					SQLadapter.getTypeName(type, SQLadapter.MEDIUMINT, true, 0, 0, false),
					SQLadapter.getTypeName(type, SQLadapter.MEDIUMINT, true, 0, 0, false)));

		mdb.disconnectDB();
	}

	// ------------------- SQL <-> Grandeza -------------------

	/**
	 * Função que associa para cada {@link Ana grandeza} de medição a sequência de
	 * caracteres correspondente. É a função inversa de
	 * {@link MDB#string2grand(String)}.
	 * 
	 * @param g item da enumeração que representa a grandeza
	 * @return caracteres que designam a grandeza
	 */
	public static String grand2string(Grandeza g) {
		if (g == null)
			return null;
		switch (g) {
		case CORRENTE:
			return "CORR";
		case CORRENTE_FALTA:
			return "CRFL";
		case CORRENTE_NEUTRO:
			return "CRNT";
		case COMPRIMENTO: // RegHist: pluviômetro
			return "MM";
		case ENR_ATIVA:
			return "ENRA";
		case ENR_REATIVA:
			return "ENRR";
		case FP:
			return "FP";
		case FREQUENCIA:
			return "FREQ";
		case N:
			return "CONT";
		case PORCENT:
			return "PORC";
		case POTENCIA:
			return "POT";
		case POT_ATIVA:
			return "POTA";
		case POT_REATIVA:
			return "POTR";
		case POT_APARENTE:
			return "POTS";
		case PRESSAO:
			return "PRES";
		case TAP:
			return "TAP";
		case TEMPERATURA:
			return "TEMP";
		case TENSAO:
			return "VOLT";
		case TENSAO_CC:
			return "VCC";
		case FLUXO:
			return "FLUX";
		case ANGULO:
			return "ANG";
		case ADIM: // RegHist: CE, LI, LS, SP
			return "PRMT";
		case RADIACAO:
			return "IRAD";
		case VELOCIDADE:
			return "VELO";
		default:
			return null;
		}
	}

	/**
	 * Função que associa para cada sequência de caracteres a {@link Ana grandeza}
	 * de medição da tag correspondente. É a função inversa de
	 * {@link MDB#grand2string(Grandeza)}.
	 * 
	 * @param s caracteres que designam a grandeza
	 * @return item da enumeração que representa a grandeza
	 */
	public static Grandeza string2grand(String s) {
		switch (s) {
		case "CORR":
			return Grandeza.CORRENTE;
		case "CRFL":
			return Grandeza.CORRENTE_FALTA;
		case "CRNT":
			return Grandeza.CORRENTE_NEUTRO;
		case "ENRA":
			return Grandeza.ENR_ATIVA;
		case "ENRR":
			return Grandeza.ENR_REATIVA;
		case "FP":
			return Grandeza.FP;
		case "FREQ":
			return Grandeza.FREQUENCIA;
		case "CONT":
			return Grandeza.N;
		case "PORC":
			return Grandeza.PORCENT;
		case "POT":
			return Grandeza.POTENCIA;
		case "MM": // RegHist: pluviômetro
			return Grandeza.COMPRIMENTO;
		case "POTA":
			return Grandeza.POT_ATIVA;
		case "POTR":
			return Grandeza.POT_REATIVA;
		case "POTS":
			return Grandeza.POT_APARENTE;
		case "PRES":
			return Grandeza.PRESSAO;
		case "TAP":
			return Grandeza.TAP;
		case "TEMP":
			return Grandeza.TEMPERATURA;
		case "VOLT":
			return Grandeza.TENSAO;
		case "VCC":
			return Grandeza.TENSAO_CC;
		case "ANG":
			return Grandeza.ANGULO;
		case "PRMT": // RegHist: CE, LI, LS, SP
			return Grandeza.ADIM;
		default:
			return null;
		}
	}
}