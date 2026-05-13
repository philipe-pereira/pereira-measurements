package br.com.pereiraeng.measurements;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Iterator;
import java.util.TreeSet;

import br.com.pereiraeng.core.Tag;
import br.com.pereiraeng.core.TimeUtils;
import br.com.pereiraeng.math.swing.chart.time.TimePeriod;
import br.com.pereiraeng.math.timeseries.esp.RegSP;
import br.com.pereiraeng.math.timeseries.unit.MedH;
import br.com.pereiraeng.math.timeseries.unit.MedV;

/**
 * Classe do objeto que retorna as medições solicitadas a uma dada fonte de
 * dados
 * 
 * @author Philipe PEREIRA
 *
 */
public abstract class MedHandler {

	// horizontal: escolhe-se previamente as tags e vai consultando os diferentes
	// instantes de tempo

	protected Tag[] h;

	public void setH(Tag[] h) {
		this.h = h;
	}

	public abstract MedH get(Calendar c);

	public abstract RegSP get(TimePeriod tp);

	public Iterator<Tag> getTags() {
		return Arrays.asList(h).iterator();
	}

	// vertical: escolhe-se os instantes de tempo e vai consultando as diferentes
	// tags

	protected Calendar[] cs;

	public abstract MedV get(Tag c);

	public abstract RegSP get(Tag[] c);

	public void setV(TimePeriod v, short step) {
		// TODO
	}

	public void setV(TreeSet<Integer> cis) {
		cs = new Calendar[cis.size()];
		int i = 0;
		for (Integer ci : cis)
			cs[i++] = TimeUtils.toCalendar(ci);
	}

	public Iterator<Calendar> getTimes() {
		return Arrays.asList(cs).iterator();
	}

	/**
	 * Função que retorna o número total de instantes de tempo do conjunto das
	 * medições
	 * 
	 * @return número total de instantes de tempo
	 */
	public int getTimeCount() {
		return cs.length;
	}

	public Calendar getStart() {
		return cs[0];
	}

	public Calendar getEnd() {
		return cs[cs.length - 1];
	}
}
