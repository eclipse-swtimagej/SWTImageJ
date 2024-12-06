// The "Table Action" macro is called when you double click
// on a table or select "Table Action" from its contextual
// (pop-up) menu. The "ROI Manager Action" macro
// is called when you double click on an ROI in the
// ROI Manager or select "ROI Manager Action" from the
// ROI Manager's contextual menu. To install these macros,
// click on "Install" or use the Macros>Install Macros
// command.

  macro "Table Action" {
     options = call("ij.Macro.getOptions");
     items = split(options,"|");
     title = items[0];
     start = items[1];
     end = items[2]
     print(title, start, end);
   }

  macro "ROI Manager Action" {
    first = roiManager("index");
    number = RoiManager.selected;
    print("RM",first,number);
 }

