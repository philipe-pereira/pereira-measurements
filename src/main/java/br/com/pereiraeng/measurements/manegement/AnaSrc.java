package br.com.pereiraeng.measurements.manegement;

import java.util.Calendar;

import br.com.pereiraeng.math.timeseries.RegP;

/**
 * Interface da classe dos objetos que representam fontes de medições analógicas
 * 
 * @author Philipe PEREIRA
 *
 */
public interface AnaSrc extends MedSrc {

	public RegP getMeds(Calendar begin, Calendar end, int freq, String metadados, String... tags);
}
