/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.refactoring;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IWorkspaceRunnable;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.text.Assert;

import org.eclipse.jdt.internal.ui.actions.WorkbenchRunnableAdapter;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

/**
 * A helper class to execute a refactoring. The class takes care of pushing the
 * undo change onto the undo stack and folding editor edits into one editor
 * undo object.
 */
public class RefactoringExecutionHelper {

	private final Refactoring fRefactoring;
	private final Shell fParent;
	private final IRunnableContext fExecContext;
	private final int fStopSeverity;
	private final boolean fNeedsSavedEditors;

	private class Operation implements IWorkspaceRunnable {
		public Change fChange;
		public PerformChangeOperation fPerformChangeOperation;
		public void run(IProgressMonitor pm) throws CoreException {
			try {
				pm.beginTask("", 11); //$NON-NLS-1$
				pm.subTask(""); //$NON-NLS-1$
				RefactoringStatus status= fRefactoring.checkAllConditions(new SubProgressMonitor(pm, 4, SubProgressMonitor.PREPEND_MAIN_LABEL_TO_SUBTASK));
				if (status.getSeverity() >= fStopSeverity) {
					RefactoringStatusDialog dialog= new RefactoringStatusDialog(fParent, status, fRefactoring.getName(), false);
					if(dialog.open() == IDialogConstants.CANCEL_ID) {
						throw new OperationCanceledException();
					}
				}
				fChange= fRefactoring.createChange(new SubProgressMonitor(pm, 2, SubProgressMonitor.PREPEND_MAIN_LABEL_TO_SUBTASK));
				fChange.initializeValidationData(new SubProgressMonitor(pm, 1, SubProgressMonitor.PREPEND_MAIN_LABEL_TO_SUBTASK));
				fPerformChangeOperation= new UIPerformChangeOperation(fChange);
				fPerformChangeOperation.setUndoManager(RefactoringCore.getUndoManager(), fRefactoring.getName());
				fPerformChangeOperation.run(new SubProgressMonitor(pm, 4, SubProgressMonitor.PREPEND_MAIN_LABEL_TO_SUBTASK));
			} finally {
				pm.done();
			}
		}
	}
	
	public RefactoringExecutionHelper(Refactoring refactoring, int stopSevertity, boolean needsSavedEditors, Shell parent, IRunnableContext context) {
		super();
		Assert.isNotNull(refactoring);
		Assert.isNotNull(parent);
		Assert.isNotNull(context);
		fRefactoring= refactoring;
		fStopSeverity= stopSevertity;
		fParent= parent;
		fExecContext= context;
		fNeedsSavedEditors= needsSavedEditors;
	}
	
	public void perform() throws InterruptedException, InvocationTargetException {
		RefactoringSaveHelper saveHelper= new RefactoringSaveHelper();
		if (fNeedsSavedEditors && !saveHelper.saveEditors(fParent))
			throw new InterruptedException();
		Operation op= new Operation();
		try{
			fExecContext.run(false, false, new WorkbenchRunnableAdapter(op));
		} catch (InvocationTargetException e) {
			Throwable inner= e.getTargetException();
			PerformChangeOperation pco= op.fPerformChangeOperation;
			if (pco.changeExecutionFailed()) {
				org.eclipse.ltk.internal.ui.refactoring.ChangeExceptionHandler handler=
					new org.eclipse.ltk.internal.ui.refactoring.ChangeExceptionHandler(fParent, fRefactoring);
				if (inner instanceof RuntimeException) {
					handler.handle(pco.getChange(), (RuntimeException)inner);
				} else if (inner instanceof CoreException) {
					handler.handle(pco.getChange(), (CoreException)inner);
				} else {
					throw e;
				}
			} else {
				throw e;
			}
		} finally {
			saveHelper.triggerBuild();
		}
	}	
}
