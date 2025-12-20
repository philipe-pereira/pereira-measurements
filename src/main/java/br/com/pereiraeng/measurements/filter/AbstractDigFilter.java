package br.com.pereiraeng.measurements.filter;

import java.util.HashMap;

/**
 * Classe abstrata do objeto que filtra uma entrada digital representada por um
 * vetor de sequência de caracteres
 * 
 * @author Philipe PEREIRA
 *
 */
public abstract class AbstractDigFilter extends HashMap<String, Boolean> {
	private static final long serialVersionUID = 2800587768245294926L;

	/**
	 * Função que determina se um dado vetor de sequência de caracteres será aceito
	 * ou não
	 * 
	 * @param row vetor de sequência de caracteres
	 * @return 0 para recusar uma entrada, qualquer inteiro diferentes de 0 para
	 *         aceitá-la
	 */
	public abstract int accept(String[] row);

}
