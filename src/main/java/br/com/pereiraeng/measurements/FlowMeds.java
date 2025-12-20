package br.com.pereiraeng.measurements;

import java.util.Iterator;
import java.util.Map.Entry;

import br.com.pereiraeng.math.timeseries.Reg;
import br.com.pereiraeng.math.timeseries.unit.Meds;
import br.com.pereiraeng.core.TimeUtils;
import br.com.pereiraeng.io.flow.Flow;

public abstract class FlowMeds implements Flow<Meds> {

	/**
	 * Função que transfere todos os dados deste registro através de {@link Med
	 * blocos de medições} para um outro objeto que interfaceia {@link Flow}, sendo
	 * que a medida que este registro é enviado, ele vai sendo apagado.
	 * 
	 * @param flow objeto que receberá as medições
	 */
	public void send(Reg reg) {
		Iterator<Entry<Integer, float[]>> it = reg.entrySet().iterator();
		Meds m = new Meds();
		while (it.hasNext()) {
			Entry<Integer, float[]> iv = it.next();

			m.setTime(TimeUtils.toCalendar(iv.getKey()));
			m.setValue(iv.getValue());

			this.incomingData(m);

			it.remove();
		}
	}

}
