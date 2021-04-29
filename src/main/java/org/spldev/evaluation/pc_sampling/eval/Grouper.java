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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.spldev.evaluation.pc_sampling.eval.analyzer.PresenceCondition;
import org.spldev.evaluation.pc_sampling.eval.analyzer.PresenceConditionList;
import org.spldev.evaluation.pc_sampling.eval.properties.GroupingProperty;
import org.spldev.formula.VariableMap;
import org.spldev.formula.clause.CNF;
import org.spldev.formula.clause.ClauseList;
import org.spldev.formula.clause.LiteralList;
import org.spldev.formula.clause.configuration.twise.TWiseCombiner;

public class Grouper {

	private CNF fmFormula;
	private Path systemPath;

	private final Object idObject = new Object();

	public Function<PresenceCondition, ?> allGrouper = pc -> idObject;
	public Function<PresenceCondition, ?> fileGrouper = PresenceCondition::getFilePath;
	public Function<PresenceCondition, ?> folderGrouper = pc -> pc.getFilePath().getParent();

	public Grouper(CNF fmFormula, String completeName) {
		this.fmFormula = fmFormula;
		systemPath = Constants.systems.resolve(completeName).toAbsolutePath().normalize();
	}

	public Expressions group(String grouping) throws Exception {
		switch (grouping) {
		case GroupingProperty.PC_ALL_FM:
		case GroupingProperty.PC_ALL:
			return group(allGrouper);
		case GroupingProperty.PC_FOLDER_FM:
		case GroupingProperty.PC_FOLDER:
			return group(folderGrouper);
		case GroupingProperty.PC_FILE_FM:
		case GroupingProperty.PC_FILE:
			return group(fileGrouper);
		case GroupingProperty.FM_ONLY:
		case GroupingProperty.PC_VARS:
			return groupVars();
		case GroupingProperty.PC_ALL_FM_FM:
			return groupVars2();
		case GroupingProperty.PC_VARS_FM:
			return groupPCFMVars();
		default:
			return null;
		}
	}

	public Expressions group(Function<PresenceCondition, ?> grouper) {
		PresenceConditionList pcList = null;
		try {
			pcList = PresenceConditionList.readPCList(systemPath.getFileName().toString(),
					fmFormula != null ? Constants.convertedPCFMFileName : Constants.convertedPCFileName);
		} catch (final FileNotFoundException e) {
		} catch (final IOException e) {
			e.printStackTrace();
		}
		if (pcList == null) {
			return null;
		}
		final Map<?, List<PresenceCondition>> groupedPCs = pcList.stream().collect(Collectors.groupingBy(grouper));
		final Expressions expressions = new Expressions();
		groupedPCs.values().stream().map(this::createExpressions).forEach(expressions.getExpressions()::add);
		expressions.setCnf(pcList.getFormula());
		return expressions;
	}

	public Expressions groupVars2() {
		PresenceConditionList pcList = null;
		try {
			pcList = PresenceConditionList.readPCList(systemPath.getFileName().toString(),
					fmFormula != null ? Constants.convertedPCFMFileName : Constants.convertedPCFileName);
		} catch (final FileNotFoundException e) {
		} catch (final IOException e) {
			e.printStackTrace();
		}
		if (pcList == null) {
			return null;
		}

		final VariableMap newVariables = pcList.getFormula().getVariableMap();
		final LinkedHashSet<ClauseList> pcs = pcList.stream().flatMap(this::createExpression)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		pcs.addAll(TWiseCombiner.convertLiterals(LiteralList.getLiterals(newVariables)).get(0));

		final Expressions expressions = new Expressions();
		expressions.setExpressions(new ArrayList<>(pcs));
		expressions.setCnf(pcList.getFormula());
		return expressions;
	}

	public Expressions groupVars() {
		PresenceConditionList pcList = null;
		try {
			pcList = PresenceConditionList.readPCList(systemPath.getFileName().toString(),
					fmFormula != null ? Constants.convertedPCFMFileName : Constants.convertedPCFileName);
		} catch (final FileNotFoundException e) {
		} catch (final IOException e) {
			e.printStackTrace();
		}
		if (pcList == null) {
			return null;
		}
		final VariableMap newVariables = pcList.getFormula().getVariableMap();

		final Expressions expressions = new Expressions();
		expressions.setExpressions(LiteralList.getLiterals(newVariables));
		expressions.setCnf(pcList.getFormula());
		return expressions;
	}

	public Expressions groupPCFMVars() {
		if (fmFormula == null) {
			throw new RuntimeException();
		}
		PresenceConditionList pcList = null;
		try {
			pcList = PresenceConditionList.readPCList(systemPath.getFileName().toString(),
					Constants.convertedPCFMFileName);
		} catch (final FileNotFoundException e) {
		} catch (final IOException e) {
			e.printStackTrace();
		}
		if (pcList == null) {
			return null;
		}
		final VariableMap newVariables = pcList.getFormula().getVariableMap();

		final Expressions expressions = new Expressions();
		expressions.setExpressions(LiteralList.getLiterals(newVariables, pcList.getPCNames()));
		expressions.setCnf(pcList.getFormula());
		return expressions;
	}

	private List<ClauseList> createExpressions(List<PresenceCondition> pcList) {
		final List<ClauseList> exps = pcList.stream() //
				.flatMap(this::createExpression) //
				.peek(Collections::sort) //
				.distinct() //
				.collect(Collectors.toList());

		sort(exps);
		return exps;
	}

	private final Stream<ClauseList> createExpression(PresenceCondition pc) {
		final Stream.Builder<ClauseList> streamBuilder = Stream.builder();
		if ((pc != null) && (pc.getDnf() != null)) {
			streamBuilder.accept(pc.getDnf().getClauses());
			streamBuilder.accept(pc.getNegatedDnf().getClauses());
		}
		return streamBuilder.build().filter(list -> !list.isEmpty());
	}

	private void sort(List<ClauseList> exps) {
		Collections.sort(exps, (Comparator<ClauseList>) (o1, o2) -> {
			final int clauseCountDiff = o1.size() - o2.size();
			if (clauseCountDiff != 0) {
				return clauseCountDiff;
			}
			int clauseLengthDiff = 0;
			for (int i = 0; i < o1.size(); i++) {
				clauseLengthDiff += o1.get(i).size() - o2.get(i).size();
			}
			return clauseLengthDiff;
		});
	}

}
