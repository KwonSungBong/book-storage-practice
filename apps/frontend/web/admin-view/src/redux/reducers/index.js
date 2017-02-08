import { combineReducers } from 'redux';
import { routerReducer } from 'react-router-redux';
import {reducer as form} from 'redux-form';
import {reducer as reduxAsyncConnect} from 'redux-connect';

import bookRecord from './bookRecord';
import bookReport from './bookReport';
import modal from './modal';

export default combineReducers({
	routing: routerReducer,
	reduxAsyncConnect,
	form,
	bookRecord,
	bookReport,
	modal,
});