package br.com.pereiraeng.measurements.manegement;

import java.util.List;
import java.util.Properties;

/**
 * Interface da classe dos objetos que representam fontes de medições, sejam
 * elas analógicas ou digitais
 * 
 * @author Philipe PEREIRA
 *
 */
public interface MedSrc {

	/**
	 * Função que estabelece as configurações da fonte de medições
	 * 
	 * @param props objeto contendo as configurações da fonte
	 */
	public void setParams(Properties props);

	/**
	 * Função que retorna a lista de subdivisões
	 * 
	 * @param classification lista a ser preenchida com as subdivisões desse nível
	 * @param args           subdivisões precedentes
	 * @return <code>true</code> se este for o último nível das subdivisões,
	 *         <code>false</code> se houver ainda outras subdivisões
	 */
	public boolean getClass(List<String> classification, String... args);

	/**
	 * Função que retorna a lista de tags
	 * 
	 * @param tags lista a ser preenchida com as tags desta subdivisão
	 * @param args subdivisão
	 */
	public void getTags(List<String> tags, String... args);
}
