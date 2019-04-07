/*
 * Copyright (C) 2019 Yasumasa Suenaga
 *
 * This file is part of ThreadDumper.
 *
 * UL Viewer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ThreadDumper is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ThreadDumper.  If not, see <http://www.gnu.org/licenses/>.
*/
module com.yasuenag.threaddumper {
    requires javafx.fxml;
    requires javafx.controls;
    
    exports com.yasuenag.threaddumper to javafx.graphics;
    opens com.yasuenag.threaddumper to javafx.fxml;
}
