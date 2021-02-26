/* -----------------------------------------------------------------------------
 * Evaluation-PC-Sampling - Program for the evalaution of PC-Sampling.
 * Copyright (C) 2021  Sebastian Krieter
 * 
 * This file is part of Evaluation-PC-Sampling.
 * 
 * Evaluation-PC-Sampling is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 * 
 * Evaluation-PC-Sampling is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Evaluation-PC-Sampling.  If not, see <https://www.gnu.org/licenses/>.
 * 
 * See <https://github.com/skrieter/evaluation-pc-sampling> for further information.
 * -----------------------------------------------------------------------------
 */
package org.spldev.evaluation.pc_sampling.eval;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.spldev.formula.clause.CNF;
import org.spldev.formula.clause.ClauseList;
import org.spldev.formula.clause.LiteralList;
import org.spldev.formula.clause.configuration.twise.TWiseCombiner;

public final class Expressions implements Serializable {

	private static final long serialVersionUID = 2430619166140896491L;

	private CNF cnf;
	private final List<List<ClauseList>> expressions = new ArrayList<>(1);

	public static Expressions readConditions(String systemName, String fileName) throws IOException {
		try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(Constants.expressionsOutput
				.resolve(systemName).resolve(fileName + "." + Constants.pcFileExtension).toString()))) {
			return (Expressions) in.readObject();
		} catch (final ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void writeConditions(Expressions expressions, String systemName, String fileName) {
		try {
			final Path filePath = Constants.expressionsOutput.resolve(systemName)
					.resolve(fileName + "." + Constants.pcFileExtension);
			Files.deleteIfExists(filePath);
			Files.createDirectories(Constants.expressionsOutput.resolve(systemName));
			try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filePath.toString()))) {
				out.writeObject(expressions);
			}
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	public CNF getCnf() {
		return cnf;
	}

	public void setCnf(CNF cnf) {
		this.cnf = cnf;
	}

	public void setExpressions(LiteralList literals) {
		expressions.clear();
		expressions.addAll(TWiseCombiner.convertLiterals(literals));
	}

	public void setExpressions(List<ClauseList> expressions) {
		this.expressions.clear();
		this.expressions.add(expressions);
	}

	public void setGroupedExpressions(List<List<ClauseList>> expressions) {
		this.expressions.clear();
		this.expressions.addAll(expressions);
	}

	public List<List<ClauseList>> getExpressions() {
		return expressions;
	}

}
