package br.com.pereiraeng.measurements;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;

import br.com.pereiraeng.math.timeseries.Reg;
import br.com.pereiraeng.math.timeseries.unit.Med;
import br.com.pereiraeng.core.TimeUtils;
import br.com.pereiraeng.io.flow.Flow;

public abstract class FlowMed implements Flow<Med> {

	/**
	 * Função que transfere os dados deste registro através de {@link Med blocos de
	 * medições} para um outro objeto que interfaceia {@link Flow}
	 * 
	 * @param flow    objeto que receberá as medições
	 * @param pos     posição dos dados neste registro
	 * @param channel endereço portado pelos {@link Med blocos de medições}
	 */
	public void transfer(Reg reg, int pos, int channel) {
		if (pos >= 0 && pos < reg.length()) {
			for (Entry<Integer, float[]> iv : reg.entrySet()) {
				Med m = new Med(TimeUtils.toCalendar(iv.getKey()), iv.getValue()[pos]);
				m.setChannel(channel);
				this.incomingData(m);
			}
		}
	}

	public void sendMed(Reg reg, int... pos) {
		Iterator<Entry<Integer, float[]>> it = reg.entrySet().iterator();
		Med m = new Med();
		while (it.hasNext()) {
			Entry<Integer, float[]> iv = it.next();

			m.setTime(TimeUtils.toCalendar(iv.getKey()));

			float[] values = iv.getValue();
			for (int i = 0; i < values.length; i++) {
				m.setValue(values[i]);
				m.setChannel(pos.length == 0 ? i : pos[i]);

				this.incomingData(m);
			}

			it.remove();
		}
	}

	public void sendMed(Reg reg, Collection<Set<Integer>> pos) {
		if (pos.size() != reg.length())
			throw new IllegalArgumentException(
					"A relação de posições não bate com o número de medições por instante de tempo");
		Iterator<Entry<Integer, float[]>> it = reg.entrySet().iterator();
		Med m = new Med();
		while (it.hasNext()) {
			Entry<Integer, float[]> iv = it.next();

			m.setTime(TimeUtils.toCalendar(iv.getKey()));

			float[] values = iv.getValue();
			Iterator<Set<Integer>> it2 = pos.iterator();
			for (int i = 0; i < values.length; i++) {
				m.setValue(values[i]);

				Set<Integer> chs = it2.next();
				for (Integer ch : chs) {
					m.setChannel(ch);
					this.incomingData(m);
				}
			}

			it.remove();
		}
	}

}
