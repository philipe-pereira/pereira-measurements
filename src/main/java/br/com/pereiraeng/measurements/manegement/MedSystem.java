package br.com.pereiraeng.measurements.manegement;

import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import br.com.pereiraeng.math.timeseries.RegP;

/**
 * Classe do objeto que modela um sistema de aquisição e publicação de medições
 * 
 * @author Philipe PEREIRA
 * @version July 30, 2020
 */
public class MedSystem {

	/**
	 * Nome do sistema de medições
	 */
	private final String system;

	/**
	 * Lista com os endereços das possíveis fontes
	 */
	protected List<MedPing> medData;

	/**
	 * Fontes dos dados
	 */
	private MedSrc medSrc;

	public MedSystem(String system) {
		this.system = system;
		medData = new LinkedList<>();
	}

	@Override
	public String toString() {
		return this.system;
	}

	public void addMedData(MedPing medData) {
		this.medData.add(medData);
	}

	public void refresh() {
		for (MedPing md : medData)
			md.check(this.system);
	}

	public void setSource(MedSrc medSrc) {
		this.medSrc = medSrc;
	}

	/**
	 * Função que retorna a lista de subdivisões
	 * 
	 * @param classification lista a ser preenchida com as subdivisões desse nível
	 * @param args           subdivisões precedentes
	 * @return <code>true</code> se este for o último nível das subdivisões,
	 *         <code>false</code> se houver ainda outras subdivisões
	 */
	public boolean getClass(List<String> classification, String... args) {
		return this.medSrc.getClass(classification, args);
	}

	/**
	 * Função que retorna a lista de tags
	 * 
	 * @param tags lista a ser preenchida com as tags desta subdivisão
	 * @param args subdivisão
	 */
	public void getTags(List<String> tags, String... args) {
		this.medSrc.getTags(tags, args);
	}

	public RegP getMeds(Calendar begin, Calendar end, int freq, String metadados, String... tags) {
		if (medSrc instanceof AnaSrc) {
			AnaSrc a = (AnaSrc) medSrc;
			return a.getMeds(begin, end, freq, metadados, tags);
		} else
			return null;
	}
}
