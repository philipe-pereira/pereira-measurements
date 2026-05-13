package br.com.pereiraeng.measurements;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import br.com.pereiraeng.core.TimeUtils;
import br.com.pereiraeng.core.collections.ArrayUtils;
import br.com.pereiraeng.geo.objetos.GeoMed;
import br.com.pereiraeng.math.Spline;
import br.com.pereiraeng.math.Vec;
import br.com.pereiraeng.math.advanced.geometry.Delaunay;
import br.com.pereiraeng.math.advanced.statistics.Sta;
import br.com.pereiraeng.math.geometry.Triangle;
import br.com.pereiraeng.math.timeseries.RegP;

/**
 * Classe das funções que produzem {@link Sta informações estatísticas} sobre
 * séries temporias
 * 
 * @author Philipe PEREIRA
 *
 */
public class MedStatUtils {

	public static final String[] RED_OPS = { "Eliminação", "Soma", "Média", "Média quadrática", "Máximo", "Mínimo" };

	public static final char EXC = 'e', SUM = '+', MED = 'm', SUM2 = '2', MAX = 'g', MIN = 'p';

	/**
	 * 
	 * @param reg         registro de medições
	 * @param freq        inteiro que indica a cadência de medições, em minutos
	 * @param cs          períodos a serem inseridos, indicados a em uma matriz com
	 *                    N linhas e 2 colunas, onde cada linha indica um período de
	 *                    tempo, delimitada inferiormente pela datas da primeira
	 *                    coluna e superiormente pela segunda coluna
	 * @param rangeLength período para se efetuar as inserções. O programa utiliza
	 *                    splines periódicos para recuperar os dados, sendo
	 *                    necessário definir um período (ou seja, um espaço de tempo
	 *                    em que as medições se repetem) Escolhe-se
	 *                    {@link Calendar#DAY_OF_MONTH} ou
	 *                    {@link Calendar#WEEK_OF_MONTH}
	 * @param pos         posições no registro
	 */
	public static void increaseFreqSpline(RegP reg, int freq, Calendar[][] cs, int rangeLength, int... pos) {
		reg.setFreq(freq);
		// tempo (em segundos; unidade mínima da discretização Reg) entre duas
		// medições
		int step = freq * 60;

		if (pos.length == 0)
			pos = ArrayUtils.progVec(true, reg.length());

		for (Calendar[] c : cs) {
			// para cada um dos sub-intervalos a serem reparados...
			Calendar b = (Calendar) c[0].clone();

			while (true) {
				Calendar[] range = TimeUtils.getRange(b, rangeLength);

				for (int p = 0; p < pos.length; p++) {
					double[][] values = reg.getMatrix(pos[p], range[0], range[1], false);

					int li0 = TimeUtils.toInt(range[0]);
					int li1 = TimeUtils.toInt(range[1]);

					// repete-se o último termo (exige-se isso para fazer o
					// Spline periódico)
					int n = values[0].length;
					values[0] = Arrays.copyOf(values[0], n + 1);
					values[0][n] = values[0][0] + li1 - li0;
					values[1] = Arrays.copyOf(values[1], n + 1);
					values[1][n] = values[1][0];

					double[] sp = Spline.getSpline(Spline.SplineType.PERIODICO, values[0], values[1]);

					for (int xi = li0; xi < li1; xi += step) {
						if (Float.isNaN(reg.get(xi, pos[p]))) {
							double y = Spline.sx(xi, values[0], values[1], sp);
							reg.put(xi, pos[p], (float) y);
						}
					}
				}

				b.add(rangeLength, 1);
				if (b.after(c[1]))
					break;
			}
		}
	}

	/**
	 * 
	 * @param reg  registro de medições
	 * @param freq inteiro que indica a cadência de medições, em minutos
	 * @param pos  posições no registro
	 */
	public static void increaseFreqSpline(RegP reg, int freq, int... pos) {
		reg.setFreq(freq);
		// tempo (em segundos; unidade mínima da discretização Reg) entre duas
		// medições
		int step = freq * 60;

		if (pos.length == 0)
			pos = ArrayUtils.progVec(true, reg.length());

		for (int p = 0; p < pos.length; p++) {
			double[][] values = reg.getMatrix(pos[p], false);
			if (values[0].length < 3)
				continue;
			double[] sp = Spline.getSpline(values[0], values[1]);

			int li0 = reg.firstKey(), li1 = reg.lastKey();
			for (int xi = li0; xi < li1; xi += step) {
				if (Float.isNaN(reg.get(xi, pos[p]))) {
					double y = Spline.sx(xi, values[0], values[1], sp);
					reg.put(xi, pos[p], (float) y);
				}
			}
		}
	}

	/**
	 * Função que reduz a frequência de registros horários, efetuando uma operação
	 * com os termos a serem eliminados
	 * 
	 * @param op      caractere indicando a operação, podendo ser:
	 *                <ul>
	 *                <li>'e' eliminação;</i>
	 *                <li>'+' soma;</i>
	 *                <li>'m' média;</i>
	 *                <li>'2' média quadrática;</i>
	 *                <li>'g' máximo;</i>
	 *                <li>'p' mínimo.</i>
	 *                </ul>
	 * @param newFreq <code>true</code> para entrar como parâmetro a nova
	 *                frequência; <code>false</code> para entrar como parâmetro o
	 *                número de agrupamentos
	 * @param param   nova frequência do registro (para <code>true</code>)
	 *                <p>
	 *                OU
	 *                </p>
	 *                número de registros a serem agrupados, sendo que o registro
	 *                final terá uma frequência igual a atual vezes este argumento
	 *                (para <code>false</code>)
	 * 
	 */
	public static void reduceFreq(RegP reg, char op, boolean newFreq, int param) {
		// contador
		int c = 1;

		if (newFreq) {
			if (param % reg.getFreq() != 0)
				throw new IllegalArgumentException(String
						.format("A nova frequência (%d') deve ser um múltiplo da antiga (%d').", param, reg.getFreq()));
		}

		Iterator<Map.Entry<Integer, float[]>> it = reg.entrySet().iterator();

		if (op == EXC) {
			// ---------------------- eliminação ----------------------

			while (it.hasNext()) {
				// para cada registro horário
				if (newFreq) {
					int ci = it.next().getKey() - 3600;
					c = ci % (param * 60);
					if (c != 0) // remover registros
						it.remove();
				} else {
					it.next();
					if (c == param) {
						// 1 vezes a cada [group] iterações
						c = 1;
					} else {
						// remover registros
						it.remove();
						c++;
					}
				}
			}
		} else {
			// ----------------------- operação -----------------------

			// inicializa os vetores
			float[] temp = new float[reg.length()];
			float[] cont = new float[reg.length()];

			float[] r = null;

			while (it.hasNext()) {
				// para cada registro horário
				Map.Entry<Integer, float[]> e = it.next();
				float[] v = e.getValue();

				// remover registros
				if (newFreq) {
					int ci = e.getKey() - 3600;
					c = ci % (param * 60);
					if (c != 0) // remover registros
						if (r != null)
							it.remove();
						else
							r = v;
					else {
						if (r != null)
							close(op, r, temp, cont);
						r = v;
					}
				} else {
					if (c == 1)
						r = v;
					else {
						// remover registros
						it.remove();
						if (c == param)
							c = 1;
						else
							c++;
					}
				}

				// verificar se não há registros vazios
				for (int i = 0; i < v.length; i++) {
					if (Float.isNaN(v[i]))
						v[i] = 0f;
					else
						cont[i]++;
				}

				// repassa os valores para um vetor temporário
				switch (op) {
				case SUM:
				case MED:
					temp = Vec.sum(temp, v);
					break;
				case MAX:
					temp = Vec.max(temp, v);
					break;
				case MIN:
					temp = Vec.min(temp, v);
					break;
				case SUM2:
					temp = Vec.sum(temp, Vec.pow(v, 2));
					break;
				}

				if (c == param && !newFreq)
					close(op, r, temp, cont);
			}
		}

		// muda frequência
		if (newFreq)
			reg.setFreq(param);
		else
			reg.setFreq(reg.getFreq() * param);
	}

	private static void close(char op, float[] r, float[] temp, float[] cont) {
		float[] result = null;
		switch (op) {
		case MED:
			result = Vec.mult(Vec.inv(cont), temp);
			break;
		case SUM2:
			result = Vec.sqrt(Vec.mult(Vec.inv(cont), temp));
			break;
		default:
			result = temp;
			break;
		}

		for (int i = 0; i < temp.length; i++) {
			r[i] = result[i];
			// zera os vetores
			temp[i] = 0f;
			cont[i] = 0f;
		}
	}

	/**
	 * Tempo máximo, em horas, que pode-se ficar sem medição que ainda dá para fazer
	 * algo (4 horas)
	 */
	public static final int RECOVERABLE = 4;

	/**
	 * Função que reconstrói os valores faltantes em um registro de dados supondo-se
	 * que tais dados são periódicos
	 * 
	 * @param reg         registro de dados
	 * @param cs          períodos a serem reparados, indicados a em uma matriz com
	 *                    N linhas e 2 colunas, onde cada linha indica um período de
	 *                    tempo, delimitada inferiormente pela datas da primeira
	 *                    coluna e superiormente pela segunda coluna
	 * @param rangeLength período para se efetuar os reparos. O programa utiliza
	 *                    splines periódicos para recuperar os dados, sendo
	 *                    necessário definir um período (ou seja, um espaço de tempo
	 *                    em que as medições se repetem) Escolhe-se
	 *                    {@link Calendar#DAY_OF_MONTH} ou
	 *                    {@link Calendar#WEEK_OF_MONTH}
	 * @param remove      <code>true</code> para remover todas as medições dos
	 *                    períodos cujas lacunas não puderem ser reparadas,
	 *                    <code>false</code> para manter os registros
	 * @param pos         posições no registro
	 */
	public static void restoreSpline(RegP reg, Calendar[][] cs, int rangeLength, boolean remove, int... pos) {
		// tempo (em minutos) entre duas medições
		int freq = reg.getFreq();
		// tempo (em segundos; unidade mínima da discretização Reg) entre duas
		// medições
		int step = freq * 60;
		// número de medições faltantes máximo
		int maxHole = RECOVERABLE * 60 / freq;

		if (pos.length == 0)
			pos = ArrayUtils.progVec(true, reg.length());

		for (Calendar[] c : cs) {
			// para cada um dos sub-intervalos a serem reparados...
			Calendar b = (Calendar) c[0].clone();

			while (true) {
				// para cada bloco periódico... (dia ou semana)
				Calendar[] range = TimeUtils.getRange(b, rangeLength);
				RegP regRange = reg.subReg(range);

				// mapear falhas
				int[][][] ls = regRange.getContinuity(range, pos);

				for (int p = 0; p < pos.length; p++) {
					if (ls[p][0][1] > 0) {
						// se falta medição...

						double[][] values = regRange.getMatrix(pos[p], false);
						if (values[0].length < 3)
							continue;

						int xi = TimeUtils.toInt(range[0]);

						// repete-se o último termo (exige-se isso para fazer o
						// Spline periódico)
						int n = values[0].length;
						values[0] = Arrays.copyOf(values[0], n + 1);
						values[0][n] = values[0][0] + TimeUtils.toInt(range[1]) - xi;
						values[1] = Arrays.copyOf(values[1], n + 1);
						values[1][n] = values[1][0];

						double[] sp = Spline.getSpline(Spline.SplineType.PERIODICO, values[0], values[1]);
						boolean repairAll = true;
						for (int i = 0; i < ls[p].length; i++) {
							if (ls[p][i][1] <= maxHole) {
								xi += ls[p][i][0] * step; // pular os que estão
															// OK
								for (int j = 0; j < ls[p][i][1]; j++) {
									// corrigir com o spline os buracos
									reg.put(xi, pos[p], (float) Spline.sx(xi, values[0], values[1], sp));
									xi += step;
								}
							} else {
								xi += step * ls[p][i][1];
								repairAll = false;
							}
						}

						if (!repairAll && remove && regRange.size() > 0) {
							// ... se não der para reparar tudo, apaga o dia
							// inteiro (se é que tem algo a ser apagado)
							for (Integer ci : regRange.keySet())
								reg.remove(ci);
						}
					}
				}

				b.add(rangeLength, 1);
				if (b.after(c[1]))
					break;
			}
		}
	}

	/**
	 * Função que reconstrói os valores faltantes em um registro de dados. Ao
	 * contrário da função
	 * {@link #restoreSpline(RegP, Calendar[][], int, boolean, int...)}, nesta
	 * função não se supõe que os sejam periódicos (o que tem como desvantagem que a
	 * função não é capaz de recuperar dados faltantes nas extremidades do
	 * intervalo)
	 * 
	 * @param reg registro de dados
	 * @param pos posições no registro
	 */
	public static void restoreSpline(RegP reg, int... pos) {
		// tempo (em minutos) entre duas medições
		int freq = reg.getFreq();
		// tempo (em segundos; unidade mínima da discretização Reg) entre duas
		// medições
		int step = freq * 60;
		// número de medições faltantes máximo
		int maxHole = RECOVERABLE * 60 / freq;

		if (pos.length == 0)
			pos = ArrayUtils.progVec(true, reg.length());

		// mapear falhas
		int[][][] ls = reg.getContinuity(null, pos);

		for (int p = 0; p < pos.length; p++) {
			if (ls[p][0][1] > 0) {
				// se falta medição...

				double[][] values = reg.getMatrix(pos[p], false);
				if (values[0].length < 3)
					continue;
				double[] sp = Spline.getSpline(values[0], values[1]);

				int xi = reg.firstKey();
				for (int i = 0; i < ls[p].length; i++) {
					// para cada buraco...

					xi += ls[p][i][0] * step; // pular os que estão OK
					if (ls[p][i][1] <= maxHole && (i == 0 ? ls[p][0][0] != 0 : true)
							&& (i == ls[p].length - 1 ? ls[p][ls[p].length - 1][1] == 0 : true)) {
						// se não for muito grande... (& não for no começo, &
						// nem no final)
						for (int j = 0; j < ls[p][i][1]; j++) {
							// corrigir com o spline os buracos
							reg.put(xi, pos[p], (float) Spline.sx(xi, values[0], values[1], sp));
							xi += step;
						}
					} else
						xi += step * ls[p][i][1];
				}
			}
		}
	}

	// ============================== GEO REPAIR ==============================

	/**
	 * Função que faz a {@link Delaunay triangulação de Delaunay} para um conjunto
	 * de pontos, porém indicando que alguns deles estão proibidos de serem
	 * utilizados
	 * 
	 * @param allRescue  tabela em que os triângulos reparadores serão incluídos
	 *                   (com a seguinte estrutura: ela associa para cada conjunto
	 *                   de pontos precisando de reparos uma outra tabela, sendo que
	 *                   esta associa os pontos em que também não há medição uma
	 *                   terceira tabela, sendo que esta última aponta para cada
	 *                   ponto o triângulo reparador correspondente)
	 * @param needRepair tabela que indica os pontos em que precisa-se calcular os
	 *                   triângulos reparadores (com a seguinte estrutura: ela
	 *                   associa para cada conjunto de pontos precisando de reparos
	 *                   um conjunto de conjuntos de pontos, sendo que tais
	 *                   conjuntos indicam os pontos em que também não há medições)
	 * @param all        conjunto com todos os pontos que podem ser utilizados
	 */
	public static void getRepair(Map<Set<GeoMed>, Map<Set<GeoMed>, Map<GeoMed, GeoMed[]>>> allRescue,
			Map<Set<GeoMed>, Set<Set<GeoMed>>> needRepair, Set<GeoMed> all) {
		for (Entry<Set<GeoMed>, Set<Set<GeoMed>>> gs : needRepair.entrySet()) {
			Set<GeoMed> precisaReparo = gs.getKey();

			all.removeAll(precisaReparo);

			Set<Set<GeoMed>> conjuntosProibidos = gs.getValue();

			for (Set<GeoMed> tbSemMed : conjuntosProibidos) {
				all.removeAll(tbSemMed);

				// ------------------------------------------------

				// pega a triangulação obtida a partir de todos os pontos, menos
				// estes (pois são eles que precisam ser corrigidos...)

				Collection<List<GeoMed>> tris = Delaunay.delaunayTriangulationF(all);

				// ver qual triângulo contém cada ponto removido
				Map<GeoMed, GeoMed[]> map = new HashMap<>();
				for (GeoMed g : precisaReparo) {
					for (List<GeoMed> t : tris) {
						if (Triangle.hasInside(g.x, g.y, t.get(0).x, t.get(0).y, t.get(1).x, t.get(1).y, t.get(2).x,
								t.get(2).y)) {
							GeoMed[] ag = t.toArray(new GeoMed[3]);
							map.put(g, ag);
							break;
						}
					}
				}
				if (map.size() == precisaReparo.size()) {
					Map<Set<GeoMed>, Map<GeoMed, GeoMed[]>> tbSemMd2solucao = allRescue.get(precisaReparo);
					if (tbSemMd2solucao == null)
						allRescue.put(precisaReparo, tbSemMd2solucao = new LinkedHashMap<>());
					tbSemMd2solucao.put(tbSemMed, map);
				} else
					System.err.println("Algum triângulo não foi encontrado...");

				// ------------------------------------------------

				all.addAll(tbSemMed);
			}
			all.addAll(precisaReparo);
		}
	}
}