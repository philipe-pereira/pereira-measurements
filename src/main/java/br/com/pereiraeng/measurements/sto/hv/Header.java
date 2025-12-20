package br.com.pereiraeng.measurements.sto.hv;

import br.com.pereiraeng.math.timeseries.esp.MedDataType;
import br.com.pereiraeng.physics.Grandeza;

/**
 * Classe do objeto que representa o cabeçalho de uma série de medições
 * 
 * @author Philipe PEREIRA
 *
 */
public class Header {

	protected MedDataType[] dataTypes;

	protected Grandeza[] as;

	public Header(MedDataType[] dataTypes, Grandeza... as) {
		if (as == null ? true : as.length == 0)
			new IllegalArgumentException();
		this.dataTypes = dataTypes;
		this.as = as;
	}

	public MedDataType[] getDataTypes() {
		return dataTypes;
	}

	public byte getBpm() {
		return (byte) MedDataType.getDataSize(dataTypes);
	}

	public byte getAnaB(int i) {
		return (byte) (as[i] == null ? -1 : as[i].ordinal());
	}

	public byte getM() {
		return (byte) as.length;
	}
}
