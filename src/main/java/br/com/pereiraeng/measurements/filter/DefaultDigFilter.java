package br.com.pereiraeng.measurements.filter;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Classe do objeto que filtra uma entrada digital representada por um vetor de
 * sequência de caracteres a partir do {@link #getPatterns() reconhecimento de
 * padrões} em determinadas {@link #getPos() posições} do vetor.
 * 
 * @author Philipe PEREIRA
 *
 */
public class DefaultDigFilter extends AbstractDigFilter {
	private static final long serialVersionUID = 6359922956599862577L;

	protected String name;

	protected int[] pos;
	protected Pattern[] patterns;
	protected boolean[] accept;

	/**
	 * Construtor do objeto que filtra entradas digitais (representadas por um linha
	 * com várias colunas)
	 * 
	 * @param name     nome do filtro
	 * @param pos      vetor com as posições no {@link #accept(String[]) vetor} a
	 *                 ser lida
	 * @param patterns vetor com os padrões a serem compilados
	 * @param accept   vetor de boolean's, contendo <code>true</code> para aceitar
	 *                 aquele padrão, <code>false</code> para negá-lo
	 */
	public DefaultDigFilter(String name, int[] pos, String[] patterns, boolean[] accept) {
		this.name = name;

		this.pos = pos;
		this.patterns = new Pattern[patterns.length];
		for (int i = 0; i < patterns.length; i++)
			this.patterns[i] = Pattern.compile(patterns[i]);
		this.accept = accept;
	}

	/**
	 * Construtor do objeto que filtra entradas digitais
	 * 
	 * @param name       nome do filtro
	 * @param content    matriz de objeto, onde o número de linhas é igual ao número
	 *                   de padrões a serem procurados e o número de colunas é 3,
	 *                   indicando:
	 *                   <ol start="0">
	 *                   <li>posição no {@link #accept(String[]) vetor} a ser
	 *                   lida;</i>
	 *                   <li>padrão a ser compilado;</i>
	 *                   <li><code>true</code> para aceitar aquele padrão,
	 *                   <code>false</code> para negá-lo.</i>
	 *                   </ol>
	 * @param protocolos tabela de dispersão que associa para cada valor um booleano
	 *                   indicando o estado
	 */
	public DefaultDigFilter(String name, Object[][] content, Map<String, Boolean> protocolos) {
		this.set(name, content, protocolos);
	}

	/**
	 * Função que estabelece os critérios do filtro de entradas digitais
	 * 
	 * @param name       nome do filtro
	 * @param content    matriz de objeto, onde o número de linhas é igual ao número
	 *                   de padrões a serem procurados e o número de colunas é 3,
	 *                   indicando:
	 *                   <ol start="0">
	 *                   <li>posição no {@link #accept(String[]) vetor} a ser
	 *                   lida;</i>
	 *                   <li>padrão a ser compilado;</i>
	 *                   <li><code>true</code> para aceitar aquele padrão,
	 *                   <code>false</code> para negá-lo.</i>
	 *                   </ol>
	 * @param protocolos tabela de dispersão que associa para cada valor um booleano
	 *                   indicando o estado
	 */
	protected void set(String name, Object[][] content, Map<String, Boolean> protocolos) {
		this.name = name;

		pos = new int[content.length];
		patterns = new Pattern[content.length];
		accept = new boolean[content.length];

		for (int i = 0; i < content.length; i++) {
			pos[i] = (int) content[i][0];
			patterns[i] = Pattern.compile((String) content[i][1]);
			accept[i] = (boolean) content[i][2];
		}

		this.clear();
		this.putAll(protocolos);
	}

	public int[] getPos() {
		return pos;
	}

	public String[] getPatterns() {
		String[] out = new String[patterns.length];
		for (int i = 0; i < patterns.length; i++)
			out[i] = patterns[i].pattern();
		return out;
	}

	public boolean[] getAccept() {
		return accept;
	}

	@Override
	public String toString() {
		return name;
	}

	@Override
	public int accept(String[] row) {
		for (int i = 0; i < pos.length; i++)
			if (patterns[i].matcher(row[pos[i]].trim()).find() ^ accept[i])
				return 0;
		return 1;
	}
}
